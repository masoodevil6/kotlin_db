package gog.my_project.data_base_schema.executor.integration

import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools

/** Narrow integration point for consumers that already own an Operation execution scope. */
object SchemaExecutionIntegration {
    private val defaultDialectSelector = DialectSelector()

    /** Renders one Schema operation and submits it to the supplied Operation context. */
    @JvmStatic
    fun executeScoped(
        operation: ISchemaApi<*>,
        scope: OperationExecutionContext,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = executeRendered(
        operation = operation,
        scope = scope,
        dialectSelector = defaultDialectSelector,
        onQueryInfo = onQueryInfo,
    ) { sql -> scope.executeDdl(sql) }

    /** SchemaExecutor path using the existing Operation DDL execution boundary. */
    internal fun executeScoped(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        dialectSelector: DialectSelector,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = executeRendered(
        operation = operation,
        scope = scope,
        dialectSelector = dialectSelector,
        onQueryInfo = onQueryInfo,
    ) { sql -> scope.executeDdl(sql) }

    /**
     * Compatibility path for callers that explicitly inject an [IQueryExecute]. The Operation
     * scope invokes it with its private Connection; Schema never receives or owns that Connection.
     */
    internal fun executeScoped(
        operation: ISchemaApi<*>,
        scope: OperationExecutionScope,
        dialectSelector: DialectSelector,
        queryExecutor: IQueryExecute,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)? = null,
    ): ExecuteResult<Boolean> = executeRendered(
        operation = operation,
        scope = scope,
        dialectSelector = dialectSelector,
        onQueryInfo = onQueryInfo,
    ) { sql -> scope.executeDdl(BuiltQuery(sql, mutableListOf()), queryExecutor) }

    /** Executes an already-rendered statement on the existing Operation scope. */
    internal fun executeRenderedScoped(
        query: BuiltQuery,
        scope: OperationExecutionScope,
    ): ExecuteResult<Boolean> = withScopeFailureHandling(scope) {
        val rejection = scope.rejectOperation()
        if (rejection != null) return@withScopeFailureHandling ExecuteResult.Failure(rejection)

        val sql = query.query ?: return@withScopeFailureHandling scope.failed(
            IllegalArgumentException("BuiltQuery query must not be null"),
        )
        val parameters = query.params.map { parameter ->
            OperationParameter(parameter.name, parameter.value, parameter.sqlType)
        }
        scope.executeDdl(sql, parameters)
    }

    /** Compatibility path for an explicitly injected legacy [IQueryExecute]. */
    internal fun executeRenderedScoped(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        queryExecutor: IQueryExecute,
    ): ExecuteResult<Boolean> = withScopeFailureHandling(scope) {
        val rejection = scope.rejectOperation()
        if (rejection != null) return@withScopeFailureHandling ExecuteResult.Failure(rejection)
        scope.executeDdl(query, queryExecutor)
    }

    private fun executeRendered(
        operation: ISchemaApi<*>,
        scope: OperationExecutionContext,
        dialectSelector: DialectSelector,
        onQueryInfo: ((sql: String, parameters: Map<String, Any?>) -> Unit)?,
        executeSql: (String) -> ExecuteResult<Boolean>,
    ): ExecuteResult<Boolean> = withScopeFailureHandling(scope) {
        val dialect = dialectSelector.select(
            scope.databasePlatformConfig.dialect,
            scope.databasePlatformConfig.targetDatabaseVersion,
        )
        val sql = requireNotNull(dialect.render(operation.ast)) {
            "Schema renderer returned no SQL for the supplied operation"
        }

        onQueryInfo?.invoke(StringTools.formatSql(sql), emptyMap())
        executeSql(sql).latchFailure(scope)
    }

    private inline fun <T> withScopeFailureHandling(
        scope: OperationExecutionContext,
        block: () -> ExecuteResult<T>,
    ): ExecuteResult<T> = try {
        block()
    } catch (error: Error) {
        scope.failed<T>(error)
        throw error
    } catch (failure: Throwable) {
        scope.failed(failure)
    }

    private fun <T> ExecuteResult<T>.latchFailure(scope: OperationExecutionContext): ExecuteResult<T> =
        if (this is ExecuteResult.Failure) scope.failed(exception) else this

}
