package gog.my_project.data_base_schema.executor.manager

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionCoordinator
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.executor.integration.SchemaExecutionIntegration
import gog.my_project.data_base_schema.renderer.manager.DialectSelector

/**
 * Executes Schema statements while Operation owns Scope, Connection, and Transaction lifecycle.
 * A custom [IQueryExecute] must support execution on a supplied Connection; SchemaExecutor never
 * delegates Connection ownership to the low-level executor.
 */
class SchemaExecutor(
    queryExecutor: IQueryExecute? = null,
    private val dialectSelector: DialectSelector = DialectSelector(),
    transactionExecutor: OperationTransactionExecutor? = null,
    val databaseContext: DatabaseContext = DatabaseContext.legacyDefault(),
) {
    private val queryExecutor: IQueryExecute = queryExecutor ?: QueryExecute(databaseContext)
    private val transactionExecutor: OperationTransactionExecutor =
        transactionExecutor ?: OperationTransactionExecutor(databaseContext)

    constructor(
        queryExecutor: IQueryExecute,
        dialectSelector: DialectSelector,
        transactionExecutor: OperationTransactionExecutor,
    ) : this(queryExecutor, dialectSelector, transactionExecutor, DatabaseContext.legacyDefault())

    private val executionCoordinator = OperationExecutionCoordinator("root schema operation")

    fun <T> transaction(
        block: (SchemaTransaction) -> ExecuteResult<T>,
    ): ExecuteResult<T> = transactionExecutor.transaction(executionCoordinator) { scope ->
        block(SchemaTransaction(this, scope))
    }

    /** Executes one operation on a newly owned Operation connection scope. */
    fun execute(
        operation: ISchemaApi<*>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) = executeRoot(blockExecute) { scope ->
        SchemaExecutionIntegration.executeScoped(
            operation,
            scope,
            dialectSelector,
            queryExecutor,
            blockQueryInfo.asQueryInfoCallback(),
        )
    }

    /** Executes already-rendered SQL on a newly owned Operation connection scope. */
    fun executeRendered(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) = executeRoot(blockExecute) { scope ->
        SchemaExecutionIntegration.executeRenderedScoped(query, scope, queryExecutor)
    }

    /** Executes already-rendered SQL on an existing Operation scope without owning its lifecycle. */
    fun executeRendered(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) = executeOnScope(scope, blockExecute) {
        SchemaExecutionIntegration.executeRenderedScoped(query, scope, queryExecutor)
    }

    /** Executes an operation on an existing Operation scope without taking lifecycle ownership. */
    fun execute(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) = executeOnScope(scope, blockExecute) {
        SchemaExecutionIntegration.executeScoped(
            operation,
            scope,
            dialectSelector,
            queryExecutor,
            blockQueryInfo.asQueryInfoCallback(),
        )
    }

    private fun executeRoot(
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        operation: (OperationExecutionScope) -> ExecuteResult<Boolean>,
    ) {
        var callbackDelivered = false
        var callbackResult: ExecuteResult<Boolean>? = null
        var callbackFailure: Throwable? = null

        val outcome = OperationExecutionExecutor(databaseContext).connection(executionCoordinator) { scope ->
            val result = operation(scope)
            callbackDelivered = true
            callbackResult = result
            try {
                blockExecute(result)
            } catch (failure: Throwable) {
                callbackFailure = failure
                scope.recordFailure(failure)
                throw failure
            }
            result
        }

        if (!callbackDelivered) {
            blockExecute(outcome)
            return
        }

        val finalFailure = outcome as? ExecuteResult.Failure
        if (finalFailure != null && callbackResult !is ExecuteResult.Failure) {
            throw finalFailure.exception
        }

        callbackFailure?.let { failure ->
            val primary = (callbackResult as? ExecuteResult.Failure)?.exception ?: failure
            if (primary !== failure) addSuppressedSafely(primary, failure)
            throw primary
        }
    }

    private fun executeOnScope(
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        operation: () -> ExecuteResult<Boolean>,
    ) {
        val result = operation()
        try {
            blockExecute(result)
        } catch (failure: Throwable) {
            val primary = scope.firstFailure ?: failure
            if (primary !== failure) addSuppressedSafely(primary, failure)
            scope.recordFailure(failure)
            throw primary
        }
    }

    private fun ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?.asQueryInfoCallback():
        ((sql: String, parameters: Map<String, Any?>) -> Unit)? = this?.let { callback ->
        { sql, parameters -> callback(sql, parameters.toMutableMap()) }
    }

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary) primary.addSuppressed(secondary)
    }
}

/** A transaction-bound Schema executor. All calls share the transaction's Operation scope. */
class SchemaTransaction internal constructor(
    private val schemaExecutor: SchemaExecutor,
    private val scope: OperationExecutionScope,
) {
    fun execute(
        operation: ISchemaApi<*>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) = schemaExecutor.execute(operation, scope, blockExecute, blockQueryInfo)
}
