package gog.my_project.data_base_schema.executor.manager

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionCoordinator
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.executor.integration.SchemaExecutionIntegration
import gog.my_project.data_base_schema.renderer.manager.DialectSelector

/**
 * Executes Schema statements while Operation owns Scope, Connection, and Transaction lifecycle.
 * A custom [IQueryExecute] must support execution on a supplied Connection. The active Operation
 * scope invokes it without transferring Connection lifecycle ownership to the executor.
 */
class SchemaExecutor(
    queryExecutor: IQueryExecute? = null,
    private val dialectSelector: DialectSelector = DialectSelector(),
    transactionExecutor: OperationTransactionExecutor? = null,
    val databaseContext: DatabaseContext = DatabaseContext.legacyDefault(),
) {
    /** Null selects OperationExecutionContext execution; explicit injection keeps legacy behavior. */
    private val queryExecutor: IQueryExecute? = queryExecutor
    private val transactionExecutor: OperationTransactionExecutor =
        transactionExecutor ?: OperationTransactionExecutor(databaseContext)
    private var eventListener: OperationEventListener? = null

    /** Adds an observer to standalone and transaction scopes while preserving the existing constructor. */
    constructor(
        databaseContext: DatabaseContext,
        eventListener: OperationEventListener,
    ) : this(
        queryExecutor = null,
        dialectSelector = DialectSelector(),
        transactionExecutor = OperationTransactionExecutor(databaseContext, eventListener),
        databaseContext = databaseContext,
    ) {
        this.eventListener = eventListener
    }

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
        executeOnScope(operation, scope, blockQueryInfo.asQueryInfoCallback())
    }

    /** Executes already-rendered SQL on a newly owned Operation connection scope. */
    fun executeRendered(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) = executeRoot(blockExecute) { scope ->
        executeRenderedOnScope(query, scope)
    }

    /** Executes already-rendered SQL on an existing Operation scope without owning its lifecycle. */
    fun executeRendered(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) = executeOnScope(scope, blockExecute) {
        executeRenderedOnScope(query, scope)
    }

    /** Executes an operation on an existing Operation scope without taking lifecycle ownership. */
    fun execute(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) = executeOnScope(scope, blockExecute) {
        executeOnScope(operation, scope, blockQueryInfo.asQueryInfoCallback())
    }

    private fun executeOnScope(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)?,
    ): ExecuteResult<Boolean> = queryExecutor?.let { injectedExecutor ->
        SchemaExecutionIntegration.executeScoped(
            operation,
            scope,
            dialectSelector,
            injectedExecutor,
            onQueryInfo,
        )
    } ?: SchemaExecutionIntegration.executeScoped(
        operation,
        scope,
        dialectSelector,
        onQueryInfo,
    )

    private fun executeRenderedOnScope(
        query: BuiltQuery,
        scope: OperationExecutionScope,
    ): ExecuteResult<Boolean> = queryExecutor?.let { injectedExecutor ->
        SchemaExecutionIntegration.executeRenderedScoped(query, scope, injectedExecutor)
    } ?: SchemaExecutionIntegration.executeRenderedScoped(query, scope)

    private fun executeRoot(
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        operation: (OperationExecutionScope) -> ExecuteResult<Boolean>,
    ) {
        var callbackDelivered = false
        var callbackResult: ExecuteResult<Boolean>? = null
        var callbackFailure: Throwable? = null

        val operationExecutor = eventListener?.let { listener ->
            OperationExecutionExecutor(databaseContext, listener)
        } ?: OperationExecutionExecutor(databaseContext)
        val outcome = operationExecutor.connection(executionCoordinator) { scope ->
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
