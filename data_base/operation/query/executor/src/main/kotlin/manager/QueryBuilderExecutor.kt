package gog.my_project.data_base_query.executor.manager

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_query.api.interfaces.api.IQueryApi
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base_query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base_query.ast.interfaces.IQueryAst
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.builder.ast.select_builder.requireResultOutputAliases
import gog.my_project.data_base_query.renderer.manager.DialectSelector
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import gog.my_project.tools.scripts.StringTools
import java.sql.Connection
import java.sql.ResultSet

class QueryBuilderExecutor private constructor(
    private val operationExecutionExecutor: OperationExecutionExecutor?,
    private val transactionScope: TransactionExecutionScope?,
    private val transactionGuard: TransactionGuard,
    private val rootExecutionCoordinator: RootExecutionCoordinator,
    private val suppliedDatabaseContext: DatabaseContext?,
    private val operationExecutionContext: OperationExecutionContext?,
) : IQueryBuilderExecutor {

    /**
     * Retained for compatibility with the existing executor contract. A Scope-only executor has
     * no DatabaseContext by design, so reading this property on that executor fails explicitly
     * instead of manufacturing a legacy/default context.
     */
    override val databaseContext: DatabaseContext
        get() = suppliedDatabaseContext ?: throw UnsupportedOperationException(
            "DatabaseContext is unavailable for a Scope-only QueryBuilderExecutor",
        )

    private val executionBackend: QueryExecutionBackend = when {
        operationExecutionContext != null -> OperationScopeQueryExecutionBackend(operationExecutionContext)
        transactionScope != null -> OperationScopeQueryExecutionBackend(transactionScope)
        else -> RootQueryExecutionBackend(
            requireNotNull(operationExecutionExecutor),
            requireNotNull(suppliedDatabaseContext),
        )
    }

    override val databasePlatformConfig
        get() = executionBackend.databasePlatformConfig

    @Suppress("DEPRECATION")
    constructor() : this(DatabaseContext.legacyDefault())

    constructor(databaseContext: DatabaseContext) : this(
        OperationExecutionExecutor(databaseContext),
        null,
        TransactionGuardRegistry.shared,
        RootExecutionCoordinator(),
        databaseContext,
        null,
    )

    /** Adds a read-only observer to Root operations without changing Query rendering or mapping. */
    constructor(databaseContext: DatabaseContext, eventListener: OperationEventListener) : this(
        OperationExecutionExecutor(databaseContext, eventListener),
        null,
        TransactionGuardRegistry.shared,
        RootExecutionCoordinator(),
        databaseContext,
        null,
    )

    internal constructor(connectionFactory: () -> Connection) :
        this(
            OperationExecutionExecutor(connectionFactory),
            null,
            TransactionGuardRegistry.shared,
            RootExecutionCoordinator(),
            DatabaseContext.legacyDefault(),
            null,
        )

    /** Retains the pre-adapter JVM constructor descriptor for existing internal integrations. */
    internal constructor(
        connectionFactory: () -> Connection,
        transactionScope: TransactionExecutionScope?,
        transactionGuard: TransactionGuard,
        rootExecutionCoordinator: RootExecutionCoordinator,
    ) : this(
        OperationExecutionExecutor(connectionFactory),
        transactionScope,
        transactionGuard,
        rootExecutionCoordinator,
        DatabaseContext.legacyDefault(),
        null,
    )

    override fun <T> connection(
        block: (ctx: IQueryBuilderExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        if (operationExecutionContext != null) {
            return executionBackend.rejectLifecycle("connection()")
        }
        if (transactionScope != null || !transactionGuard.enter(rootExecutionCoordinator)) {
            val failure = IllegalStateException("Nested connection scopes are not supported")
            transactionScope?.recordFailure(failure)
            transactionGuard.recordNestedAttempt(failure)
            return ExecuteResult.Failure(failure)
        }

        return try {
            requireNotNull(operationExecutionExecutor).connection(rootExecutionCoordinator.operationCoordinator()) { scope ->
                transactionGuard.attach(scope)
                val scopedExecutor = QueryBuilderExecutor(
                    operationExecutionExecutor,
                    scope,
                    transactionGuard,
                    rootExecutionCoordinator,
                    databaseContext,
                    null,
                )
                block(scopedExecutor)
            }
        } finally {
            transactionGuard.exit()
        }
    }

    override fun <T> transaction(
        block: (tx: IQueryBuilderExecutor) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        if (operationExecutionContext != null) {
            return executionBackend.rejectLifecycle("transaction()")
        }
        if (transactionScope != null || !transactionGuard.enter(rootExecutionCoordinator)) {
            val failure = IllegalStateException("Nested transactions are not supported")
            transactionScope?.recordFailure(failure)
            transactionGuard.recordNestedAttempt(failure)
            return ExecuteResult.Failure(failure)
        }

        return try {
            requireNotNull(operationExecutionExecutor).transaction(rootExecutionCoordinator.operationCoordinator()) { scope ->
                transactionGuard.attach(scope)
                val scopedExecutor = QueryBuilderExecutor(
                    operationExecutionExecutor,
                    scope,
                    transactionGuard,
                    rootExecutionCoordinator,
                    databaseContext,
                    null,
                )
                try {
                    block(scopedExecutor)
                } catch (error: Error) {
                    // Seal happens before the shared lifecycle layer receives the throwable.
                    // Latch it while the scope is still active so Error remains fatal.
                    scope.recordFatal(error)
                    throw error
                } catch (exception: Exception) {
                    // Preserve the callback failure in the shared scope before sealing it.
                    val failure = scope.recordFailure(exception)
                    if (failure !== exception) addSuppressedSafely(failure, exception)
                    ExecuteResult.Failure(failure)
                }
            }
        } finally {
            transactionGuard.exit()
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(databasePlatformConfig.dialect)
        when (val rendered = QueryParameterRendering.render(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                executionBackend.executeSelect(QueryExecutionStatement.Built(builtQuery), callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                executionBackend.executeSelect(QueryExecutionStatement.Bound(boundQuery), callback)
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderInsertApi,
        blockExecute: (ExecuteResult<Long>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(databasePlatformConfig.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                executionBackend.executeInsert(QueryExecutionStatement.Built(builtQuery), callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                executionBackend.executeInsert(QueryExecutionStatement.Bound(boundQuery), callback)
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderUpdateApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(databasePlatformConfig.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                executionBackend.executeUpdate(QueryExecutionStatement.Built(builtQuery), callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                executionBackend.executeUpdate(QueryExecutionStatement.Bound(boundQuery), callback)
            }
        }
    }

    override fun execute(
        queryBuilder: IQueryRenderDeleteApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val dialect = DialectSelector().select(databasePlatformConfig.dialect)
        when (val rendered = QueryParameterRendering.renderDml(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> {
                val builtQuery = legacyBuiltQuery(rendered, blockQueryInfo)
                executionBackend.executeDelete(QueryExecutionStatement.Built(builtQuery), callback)
            }
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                val boundQuery = BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
                executionBackend.executeDelete(QueryExecutionStatement.Bound(boundQuery), callback)
            }
        }
    }

    override fun first(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<gog.my_project.data_base_query.executor.result.QueryRow>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val columns = selectOutputColumnsForExecution(queryBuilder, callback) ?: return@executeInScope
        val statement = renderSelectStatement(queryBuilder, blockQueryInfo)
        executionBackend.selectRows(statement, columns, firstOnly = true) { rows ->
            callback(
                when (rows) {
                    is ExecuteResult.Failure -> rows
                    is ExecuteResult.Success -> ExecuteResult.Success(rows.result?.firstOrNull())
                },
            )
        }
    }

    override fun get(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<List<gog.my_project.data_base_query.executor.result.QueryRow>>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = executeInScope(blockExecute) { callback ->
        val columns = selectOutputColumnsForExecution(queryBuilder, callback) ?: return@executeInScope
        val statement = renderSelectStatement(queryBuilder, blockQueryInfo)
        executionBackend.selectRows(statement, columns, firstOnly = false, callback = callback)
    }

    internal fun recordConsumerThrowable(failure: Throwable) {
        if (operationExecutionContext != null || transactionScope != null) {
            executionBackend.recordConsumerThrowable(failure)
        } else {
            transactionGuard.activeScope()?.recordFailure(failure)
        }
    }

    override fun <Ast : IQueryAst<*>, Api : IQueryApi<Ast>> executeOut(
        queryBuilder: Api,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        blockResult: (db: gog.my_project.data_base_manager.execute.interfaces.IQueryExecute, builtQuery: BuiltQuery) -> Unit,
    ) {
        if (operationExecutionContext != null) {
            throw executionBackend.rejectLifecycle("executeOut").exception
        }
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
        if (operationExecutionContext != null) {
            executeInOperationContext(blockExecute, operation)
            return
        }
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

        val rejection = scope.rejectOperation()
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

    private fun <T> executeInOperationContext(
        blockExecute: (ExecuteResult<T>) -> Unit,
        operation: (callback: (ExecuteResult<T>) -> Unit) -> Unit,
    ) {
        val scope = requireNotNull(operationExecutionContext)
        var delivered = false
        val deliver: (ExecuteResult<T>) -> Unit = { result ->
            check(!delivered) { "Query execution delivered more than one result" }
            delivered = true
            blockExecute(result)
        }

        try {
            operation(deliver)
            if (!delivered) {
                deliver(scope.failed(IllegalStateException("Scope-bound query execution completed without a result")))
            }
        } catch (error: Error) {
            scope.failed<Any?>(error)
            throw error
        } catch (failure: Throwable) {
            val recorded = scope.failed<T>(failure)
            if (delivered) throw failure
            deliver(recorded)
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

    private fun renderSelectStatement(
        queryBuilder: IQueryRenderSelectApi,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ): QueryExecutionStatement {
        val dialect = DialectSelector().select(databasePlatformConfig.dialect)
        return when (val rendered = QueryParameterRendering.render(queryBuilder.ast, dialect)) {
            is LegacyQueryRenderResult -> QueryExecutionStatement.Built(legacyBuiltQuery(rendered, blockQueryInfo))
            is IdentityAwareQueryRenderResult -> {
                reportIdentityAwareQuery(rendered, blockQueryInfo)
                QueryExecutionStatement.Bound(BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences))
            }
        }
    }

    private fun <T> selectOutputColumnsForExecution(
        queryBuilder: IQueryRenderSelectApi,
        callback: (ExecuteResult<T>) -> Unit,
    ): List<gog.my_project.data_base_query.ast.interfaces.select_interface.column.IQueryColumnsAst>? {
        try {
            queryBuilder.requireResultOutputAliases()
        } catch (failure: IllegalArgumentException) {
            return deliverSelectValidationFailure(callback, failure)
        }

        val columns = queryBuilder.ast.select!!.columns
        val missingExecutionType = columns.indexOfFirst { it.metadata.ExecutionType == null }
        if (missingExecutionType >= 0) {
            val column = columns[missingExecutionType]
            return deliverSelectValidationFailure(
                callback,
                IllegalArgumentException(
                    "SELECT output at index $missingExecutionType ('${column.metadata.ColumnAlias}') must declare an execution DataType",
                ),
            )
        }
        return columns
    }

    private fun <T> deliverSelectValidationFailure(
        callback: (ExecuteResult<T>) -> Unit,
        failure: IllegalArgumentException,
    ): Nothing? {
        // Match the public default first/get contract for Root validation failures. Managed scopes
        // keep using executeInScope's normal failure-latching path.
        if (transactionScope == null && operationExecutionContext == null) {
            callback(ExecuteResult.Failure(failure))
            return null
        }
        throw failure
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

    companion object {
        /** Creates a Query facade backed only by the caller-owned Operation scope. */
        @JvmStatic
        fun scopeBound(
            operationExecutionContext: OperationExecutionContext,
        ): QueryBuilderExecutor = QueryBuilderExecutor(
            null,
            null,
            TransactionGuardRegistry.shared,
            RootExecutionCoordinator(),
            null,
            operationExecutionContext,
        )

        /** Creates an additive Query facade that executes only through an existing Operation scope. */
        @JvmStatic
        fun scopeBound(
            databaseContext: DatabaseContext,
            operationExecutionContext: OperationExecutionContext,
        ): QueryBuilderExecutor = QueryBuilderExecutor(
            null,
            null,
            TransactionGuardRegistry.shared,
            RootExecutionCoordinator(),
            databaseContext,
            operationExecutionContext,
        )
    }

}

/** Routes post-materialization consumer failures into the active executor scope, when present. */
internal fun recordScopedConsumerThrowable(
    executor: IQueryBuilderExecutor,
    failure: Throwable,
) {
    (executor as? QueryBuilderExecutor)?.recordConsumerThrowable(failure)
}
