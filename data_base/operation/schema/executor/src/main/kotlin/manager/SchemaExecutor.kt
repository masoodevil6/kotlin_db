package gog.my_project.data_base_schema.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionCoordinator
import gog.my_project.data_base_operation.executor.transaction.OperationTransactionExecutor
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools

/** Executes schema operations; transaction lifecycle is delegated to the shared Operation executor. */
class SchemaExecutor(
    private val queryExecutor: IQueryExecute = QueryExecute(),
    private val dialectSelector: DialectSelector = DialectSelector(),
    private val transactionExecutor: OperationTransactionExecutor = OperationTransactionExecutor(),
) {
    private val executionCoordinator = OperationExecutionCoordinator("root schema operation")

    fun <T> transaction(
        block: (SchemaTransaction) -> ExecuteResult<T>,
    ): ExecuteResult<T> = transactionExecutor.transaction(executionCoordinator) { scope ->
        block(SchemaTransaction(this, scope))
    }

    fun execute(
        operation: ISchemaApi<*>,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) {
        withRootOperation(blockExecute) {
            val query = render(operation, blockQueryInfo)
            executeRenderedOwned(query, blockExecute)
        }
    }

    /** Executes already-rendered schema SQL using the low-level executor's normal owned-connection path. */
    fun executeRendered(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        withRootOperation(blockExecute) {
            executeRenderedOwned(query, blockExecute)
        }
    }

    private fun executeRenderedOwned(
        query: BuiltQuery,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        queryExecutor.executeTable(query, blockExecute)
    }

    private fun withRootOperation(
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        operation: () -> Unit,
    ) {
        val failure = IllegalStateException(
            "Root schema execution cannot run independently while a managed execution scope is active",
        )
        when (val admission = executionCoordinator.acquireRootOperation(failure)) {
            is OperationExecutionCoordinator.OperationAdmission.Rejected -> {
                try {
                    blockExecute(ExecuteResult.Failure(admission.failure))
                } catch (throwable: Throwable) {
                    executionCoordinator.recordRejectedCallbackThrowable(throwable)
                    throw throwable
                }
            }
            is OperationExecutionCoordinator.OperationAdmission.Allowed -> try {
                operation()
            } finally {
                admission.permit.release()
            }
        }
    }

    /** Executes a rendered schema operation through manager:execute on the supplied scope connection. */
    fun executeRendered(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
    ) {
        val rejection = scope.rejectOperation()
        if (rejection != null) {
            val failure = ExecuteResult.Failure(rejection)
            scope.recordResult(failure)
            blockExecute(failure)
            return
        }

        queryExecutor.executeTable(scope.connection, query) { result ->
            scope.recordResult(result)
            blockExecute(result)
        }
    }

    /** Executes an operation on an existing Operation scope without taking ownership of its lifecycle. */
    fun execute(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        blockExecute: (ExecuteResult<Boolean>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)? = null,
    ) {
        val rejection = scope.rejectOperation()
        if (rejection != null) {
            val failure = ExecuteResult.Failure(rejection)
            scope.recordResult(failure)
            blockExecute(failure)
            return
        }
        executeRendered(render(operation, blockQueryInfo), scope, blockExecute)
    }

    private fun render(
        operation: ISchemaApi<*>,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ): BuiltQuery {
        val dialect = dialectSelector.select(DefaultDatabaseConfig.config.dialect)
        val sql = requireNotNull(dialect.render(operation.ast)) { "rendered schema SQL is null" }
        val params = operation.params
        blockQueryInfo?.invoke(
            StringTools.formatSql(sql),
            params.associateTo(linkedMapOf()) { it.name to it.value },
        )
        return BuiltQuery(sql, params.toMutableList())
    }
}

/** A transaction-bound schema executor. All calls share the transaction's Operation scope. */
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
