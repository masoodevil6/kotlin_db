package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.manager.connection.manager.DatabaseConnection
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.query.BoundQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import gog.my_project.tools.scripts.StringTools
import java.sql.Connection
import java.sql.ResultSet

class QueryBuilderExecutor internal constructor(
    private val connectionFactory: () -> Connection,
    private val transactionScope: TransactionExecutionScope?,
    private val transactionGuard: TransactionGuard,
    private val rootExecutionCoordinator: RootExecutionCoordinator,
) : IQueryBuilderExecutor {

    constructor() : this(
        { DatabaseConnection().build() },
        null,
        TransactionGuardRegistry.shared,
        RootExecutionCoordinator(),
    )

    internal constructor(connectionFactory: () -> Connection) :
        this(connectionFactory, null, TransactionGuardRegistry.shared, RootExecutionCoordinator())

    override fun <T> connection(
        block: (ctx: IQueryBuilderExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        if (transactionScope != null || !transactionGuard.enter(rootExecutionCoordinator)) {
            val failure = IllegalStateException("Nested connection scopes are not supported")
            transactionScope?.recordFailure(failure)
            transactionGuard.recordNestedAttempt(failure)
            return ExecuteResult.Failure(failure)
        }

        val startFailure = rootExecutionCoordinator.tryStartScope(
            IllegalStateException("Nested connection scopes are not supported"),
        )
        if (startFailure != null) {
            transactionGuard.exit()
            return ExecuteResult.Failure(startFailure)
        }

        return try {
            executeConnectionScope(block)
        } finally {
            try {
                rootExecutionCoordinator.finishScope()
            } finally {
                transactionGuard.exit()
            }
        }
    }

    private fun <T> executeConnectionScope(
        block: (ctx: IQueryBuilderExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val connection = try {
            connectionFactory()
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            return ExecuteResult.Failure(exception)
        }

        val preparedScope = try {
            val scope = TransactionExecutionScope(connection)
            rootExecutionCoordinator.activate(scope)
            transactionGuard.attach(scope)
            scope to QueryBuilderExecutor(connectionFactory, scope, transactionGuard, rootExecutionCoordinator)
        } catch (error: Error) {
            closeConnectionScope(connection, error)
            throw error
        } catch (exception: Exception) {
            val closeOutcome = closeConnectionScope(connection, exception)
            if (closeOutcome is Error) throw closeOutcome
            return ExecuteResult.Failure(exception)
        }
        val scope = preparedScope.first
        val scopedExecutor = preparedScope.second

        var callbackResult: ExecuteResult<T>? = null
        var callbackException: Exception? = null
        var callbackError: Error? = null
        try {
            callbackResult = block(scopedExecutor)
        } catch (error: Error) {
            callbackError = error
            scope.recordFatal(error)
        } catch (exception: Exception) {
            callbackException = exception
        } finally {
            rootExecutionCoordinator.seal(scope)
        }

        val fatalError = scope.fatalError ?: callbackError
        if (fatalError != null) {
            closeConnectionScope(connection, fatalError)
            throw fatalError
        }

        val scopedFailure = scope.firstFailure
        if (scopedFailure != null) {
            if (callbackException != null && callbackException !== scopedFailure) {
                addSuppressedSafely(scopedFailure, callbackException)
            }
            val closeOutcome = closeConnectionScope(connection, scopedFailure)
            if (closeOutcome is Error) throw closeOutcome
            return ExecuteResult.Failure(scopedFailure)
        }

        if (callbackException != null) {
            val closeOutcome = closeConnectionScope(connection, callbackException)
            if (closeOutcome is Error) throw closeOutcome
            return ExecuteResult.Failure(callbackException)
        }

        val result = callbackResult
            ?: run {
                val missingResult = IllegalStateException("Connection-scope callback returned no result")
                val closeOutcome = closeConnectionScope(connection, missingResult)
                if (closeOutcome is Error) throw closeOutcome
                return ExecuteResult.Failure(missingResult)
            }

        if (result is ExecuteResult.Failure) {
            val closeOutcome = closeConnectionScope(connection, result.exception)
            if (closeOutcome is Error) throw closeOutcome
            return result
        }

        val closeFailure = closeConnectionScope(connection, null)
        if (closeFailure is Error) throw closeFailure
        if (closeFailure is Exception) {
            return ExecuteResult.Failure(
                IllegalStateException(
                    "Connection scope completed without commit or rollback, but closing its connection failed; prior changes may already be durable, so do not retry blindly",
                    closeFailure,
                ),
            )
        }
        return result
    }

    override fun <T> transaction(
        block: (tx: IQueryBuilderExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        if (transactionScope != null || !transactionGuard.enter(rootExecutionCoordinator)) {
            val failure = IllegalStateException("Nested transactions are not supported")
            transactionScope?.recordFailure(failure)
            transactionGuard.recordNestedAttempt(failure)
            return ExecuteResult.Failure(failure)
        }

        val startFailure = rootExecutionCoordinator.tryStartScope(
            IllegalStateException("Nested transactions are not supported"),
        )
        if (startFailure != null) {
            transactionGuard.exit()
            return ExecuteResult.Failure(startFailure)
        }

        return try {
            executeTransaction(block)
        } finally {
            try {
                rootExecutionCoordinator.finishScope()
            } finally {
                transactionGuard.exit()
            }
        }
    }

    private fun <T> executeTransaction(
        block: (tx: IQueryBuilderExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val connection = try {
            connectionFactory()
        } catch (error: Error) {
            throw error
        } catch (exception: Exception) {
            return ExecuteResult.Failure(exception)
        }

        try {
            connection.autoCommit = false
        } catch (error: Error) {
            val fatal = closeAfterFailure(connection, error)
            throw fatal ?: error
        } catch (exception: Exception) {
            val fatal = closeAfterFailure(connection, exception)
            if (fatal != null) throw fatal
            return ExecuteResult.Failure(exception)
        }

        val scope = TransactionExecutionScope(connection)
        rootExecutionCoordinator.activate(scope)
        transactionGuard.attach(scope)
        val scopedExecutor = QueryBuilderExecutor(connectionFactory, scope, transactionGuard, rootExecutionCoordinator)

        var callbackResult: ExecuteResult<T>? = null
        var callbackException: Exception? = null
        var callbackError: Error? = null
        try {
            callbackResult = block(scopedExecutor)
        } catch (error: Error) {
            callbackError = error
            scope.recordFatal(error)
        } catch (exception: Exception) {
            callbackException = exception
        } finally {
            rootExecutionCoordinator.seal(scope)
        }

        val fatalError = scope.fatalError ?: callbackError
        if (fatalError != null) {
            val cleanupError = rollbackAndClose(connection, fatalError)
            throw cleanupError ?: fatalError
        }

        val queryFailure = scope.firstFailure
        if (queryFailure != null) {
            val cleanupError = rollbackAndClose(connection, queryFailure)
            if (cleanupError != null) throw cleanupError
            return ExecuteResult.Failure(queryFailure)
        }

        if (callbackException != null) {
            val cleanupError = rollbackAndClose(connection, callbackException)
            if (cleanupError != null) throw cleanupError
            return ExecuteResult.Failure(callbackException)
        }

        val result = callbackResult
            ?: run {
                val missingResult = IllegalStateException("Transaction callback returned no result")
                val cleanupError = rollbackAndClose(connection, missingResult)
                if (cleanupError != null) throw cleanupError
                return ExecuteResult.Failure(missingResult)
            }

        if (result is ExecuteResult.Failure) {
            val cleanupError = rollbackAndClose(connection, result.exception)
            if (cleanupError != null) throw cleanupError
            return result
        }

        try {
            connection.commit()
        } catch (error: Error) {
            val cleanupError = rollbackAndClose(connection, error)
            throw cleanupError ?: error
        } catch (exception: Exception) {
            val commitFailure = TransactionCommitException(exception)
            val cleanupError = rollbackAndClose(connection, commitFailure)
            if (cleanupError != null) throw cleanupError
            return ExecuteResult.Failure(commitFailure)
        }

        try {
            connection.close()
        } catch (error: Error) {
            throw TransactionCompletionError(committed = true, cause = error)
        } catch (exception: Exception) {
            return ExecuteResult.Failure(
                TransactionCompletionException(committed = true, cause = exception),
            )
        }

        return result
    }

    override fun execute(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.render(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                val executor = QueryExecute()
                if (transactionScope == null) {
                    executor.executeSelect(builtQuery, callback)
                } else {
                    executor.executeSelect(transactionScope.connection, builtQuery, callback)
                }
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                val executor = QueryExecute()
                if (transactionScope == null) {
                    executor.executeSelect(boundQuery, callback)
                } else {
                    executor.executeSelect(transactionScope.connection, boundQuery, callback)
                }
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderInsertApi,
        blockExecute: (ExecuteResult<Long>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val executor = QueryExecute()
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                if (transactionScope == null) executor.executeInsert(builtQuery, callback)
                else executor.executeInsert(transactionScope.connection, builtQuery, callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                val executor = QueryExecute()
                if (transactionScope == null) executor.executeInsert(boundQuery, callback)
                else executor.executeInsert(transactionScope.connection, boundQuery, callback)
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderUpdateApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val executor = QueryExecute()
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                if (transactionScope == null) executor.executeUpdate(builtQuery, callback)
                else executor.executeUpdate(transactionScope.connection, builtQuery, callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                val executor = QueryExecute()
                if (transactionScope == null) executor.executeUpdate(boundQuery, callback)
                else executor.executeUpdate(transactionScope.connection, boundQuery, callback)
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderDeleteApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val executor = QueryExecute()
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                if (transactionScope == null) executor.executeDelete(builtQuery, callback)
                else executor.executeDelete(transactionScope.connection, builtQuery, callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                val executor = QueryExecute()
                if (transactionScope == null) executor.executeDelete(boundQuery, callback)
                else executor.executeDelete(transactionScope.connection, boundQuery, callback)
            }
        }
    }

    override fun first(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<gog.my_project.data_base.query.executer.result.QueryRow>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        super<IQueryBuilderExecutor>.first(queryBuilder, callback, blockQueryInfo)
    }

    override fun get(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<List<gog.my_project.data_base.query.executer.result.QueryRow>>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        super<IQueryBuilderExecutor>.get(queryBuilder, callback, blockQueryInfo)
    }

    internal fun recordConsumerThrowable(failure: Throwable) {
        (transactionScope ?: transactionGuard.activeScope())?.recordFailure(failure)
    }

    override fun <Ast : IQueryAst<*>, Api : IQueryApi<Ast>> executeOut(
        queryBuilder: Api,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        blockResult: (db: gog.my_project.data_base.manager.execute.interfaces.IQueryExecute, builtQuery: BuiltQuery) -> Unit,
    ) {
        val activeScope = transactionScope ?: transactionGuard.activeScope()
        if (activeScope != null) {
            val failure = UnsupportedOperationException(
                "executeOut is not available inside a managed execution scope; use the scoped queryBuilder API",
            )
            activeScope.recordFailure(failure)
            throw failure
        }

        val failure = UnsupportedOperationException(
            "executeOut is not available while a managed execution scope is active; use the scoped queryBuilder API",
        )
        when (val admission = rootExecutionCoordinator.acquireRootOperation(failure)) {
            is RootExecutionCoordinator.OperationAdmission.Rejected -> throw failure
            is RootExecutionCoordinator.OperationAdmission.Allowed -> {
                try {
                    super<IQueryBuilderExecutor>.executeOut(queryBuilder, blockQueryInfo, blockResult)
                } finally {
                    admission.permit.release()
                }
            }
        }
    }

    private fun <T> executeInScope(
        blockExecute: (ExecuteResult<T>) -> Unit,
        operation: (callback: (ExecuteResult<T>) -> Unit) -> Unit,
    ) {
        val scope = transactionScope
        if (scope == null) {
            val enclosingScope = transactionGuard.activeScope()
            if (enclosingScope != null) {
                val rejection = enclosingScope.recordFailure(
                    IllegalStateException(
                        "Use the executor passed to the active scope callback for scoped queries",
                    ),
                )
                deliverRejectedRootOperation(blockExecute, rejection) { failure ->
                    if (failure is Error) enclosingScope.recordFatal(failure)
                    else enclosingScope.recordFailure(failure)
                }
                return
            }

            val rootFailure = IllegalStateException(
                "Root executor cannot run independently while its managed execution scope is active",
            )
            when (val admission = rootExecutionCoordinator.acquireRootOperation(rootFailure)) {
                is RootExecutionCoordinator.OperationAdmission.Rejected ->
                    deliverRejectedRootOperation(blockExecute, admission.failure) { failure ->
                        rootExecutionCoordinator.recordRejectedCallbackThrowable(failure)
                    }
                is RootExecutionCoordinator.OperationAdmission.Allowed -> try {
                    operation(blockExecute)
                } finally {
                    admission.permit.release()
                }
            }
            return
        }

        val rejection = scope.rejectQuery()
        var delivered = false
        val deliver: (ExecuteResult<T>) -> Unit = { result ->
            check(!delivered) { "Query execution delivered more than one result" }
            delivered = true
            scope.recordResult(result)
            try {
                blockExecute(result)
            } catch (error: Error) {
                scope.recordFatal(error)
                throw error
            } catch (exception: Exception) {
                scope.recordFailure(exception)
                throw exception
            }
        }

        try {
            if (rejection != null) {
                deliver(ExecuteResult.Failure(rejection))
            } else {
                operation(deliver)
            }
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (exception: Exception) {
            if (delivered) {
                scope.recordFailure(exception)
                throw exception
            }
            deliver(ExecuteResult.Failure(scope.recordFailure(exception)))
        }
    }

    private fun <T> deliverRejectedRootOperation(
        blockExecute: (ExecuteResult<T>) -> Unit,
        failure: Throwable,
        recordThrowable: (Throwable) -> Unit,
    ) {
        try {
            blockExecute(ExecuteResult.Failure(failure))
        } catch (throwable: Throwable) {
            recordThrowable(throwable)
            throw throwable
        }
    }

    private fun closeAfterFailure(connection: Connection, primary: Throwable): Error? {
        try {
            connection.close()
        } catch (failure: Error) {
            if (primary is Error) {
                addSuppressedSafely(primary, failure)
                return primary
            }
            addSuppressedSafely(failure, primary)
            return failure
        } catch (failure: Exception) {
            addSuppressedSafely(primary, failure)
        }
        return primary as? Error
    }

    /** Attempts the scope-owned close once, retaining the primary failure according to scope policy. */
    private fun closeConnectionScope(connection: Connection, primary: Throwable?): Throwable? {
        val closeFailure = try {
            connection.close()
            return primary
        } catch (error: Error) {
            error
        } catch (exception: Exception) {
            exception
        }

        if (primary is Error) {
            addSuppressedSafely(primary, closeFailure)
            return primary
        }
        if (closeFailure is Error) {
            if (primary != null) addSuppressedSafely(closeFailure, primary)
            return closeFailure
        }
        if (primary != null) {
            addSuppressedSafely(primary, closeFailure)
            return primary
        }
        return closeFailure
    }

    /** Returns a fatal cleanup Error when one occurred; ordinary cleanup failures stay suppressed. */
    private fun rollbackAndClose(connection: Connection, primary: Throwable): Error? {
        var fatal: Error? = primary as? Error
        try {
            connection.rollback()
        } catch (failure: Error) {
            if (fatal == null) {
                fatal = failure
                addSuppressedSafely(failure, primary)
            } else {
                addSuppressedSafely(fatal, failure)
            }
        } catch (failure: Exception) {
            addSuppressedSafely(primary, failure)
        }

        try {
            connection.close()
        } catch (failure: Error) {
            if (fatal == null) {
                fatal = failure
                addSuppressedSafely(failure, primary)
            } else {
                addSuppressedSafely(fatal, failure)
            }
        } catch (failure: Exception) {
            addSuppressedSafely(fatal ?: primary, failure)
        }
        return fatal
    }

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary) primary.addSuppressed(secondary)
    }

    private fun legacyBuiltQuery(
        rendered: LegacyQueryRenderResult,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ): BuiltQuery {
        val sql = requireNotNull(rendered.sql) { "rendered sql is null" }
        if (blockQueryInfo != null) {
            blockQueryInfo(StringTools.formatSql(sql), mutableMapOf())
        }
        return BuiltQuery(sql, mutableListOf())
    }

    private fun reportIdentityAwareQuery(
        rendered: IdentityAwareQueryRenderResult,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        if (blockQueryInfo != null) {
            val paramsMap = parameterMap(rendered.parameterOccurrences)
            blockQueryInfo(StringTools.formatSql(rendered.displaySql), paramsMap)
        }
    }

    /** The callback exposes the first emitted value for each name; binding order stays in the render result. */
    private fun parameterMap(
        occurrences: List<IQueryParameterBinding>,
    ): MutableMap<String, Any?> = linkedMapOf<String, Any?>().apply {
        occurrences.forEach { parameter ->
            putIfAbsent(parameter.name, parameter.value)
        }
    }

}

/** Routes post-materialization consumer failures into the active executor scope, when present. */
internal fun recordScopedConsumerThrowable(
    executor: IQueryBuilderExecutor,
    failure: Throwable,
) {
    (executor as? QueryBuilderExecutor)?.recordConsumerThrowable(failure)
}
