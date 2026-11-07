package gog.my_project.data_base_schema.executor.integration

import gog.my_project.data_base_manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
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

    /**
     * SchemaExecutor entry point. Rendering stays shared with the facade path, while the injected
     * low-level executor receives the already-owned scope connection and cannot own its lifecycle.
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
    ) { sql -> executeThroughQueryExecutor(BuiltQuery(sql, mutableListOf()), scope, queryExecutor) }

    /** Executes an already-rendered statement on the existing Operation scope. */
    internal fun executeRenderedScoped(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        queryExecutor: IQueryExecute,
    ): ExecuteResult<Boolean> = withScopeFailureHandling(scope) {
        val rejection = scope.rejectOperation()
        if (rejection != null) return@withScopeFailureHandling ExecuteResult.Failure(rejection)
        executeThroughQueryExecutor(query, scope, queryExecutor)
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

    private fun executeThroughQueryExecutor(
        query: BuiltQuery,
        scope: OperationExecutionScope,
        queryExecutor: IQueryExecute,
    ): ExecuteResult<Boolean> {
        val rejection = scope.rejectOperation()
        if (rejection != null) return ExecuteResult.Failure(rejection)

        var callbackCount = 0
        var callbackResult: ExecuteResult<Boolean>? = null
        return try {
            queryExecutor.executeTable(scope.connection, query) { result ->
                callbackCount++
                if (callbackCount == 1) {
                    callbackResult = result
                    scope.recordResult(result)
                } else {
                    val duplicateCallback = IllegalStateException(
                        "Schema query executor delivered more than one execution result",
                    )
                    scope.recordFailure(duplicateCallback)
                    callbackResult = ExecuteResult.Failure(scope.firstFailure ?: duplicateCallback)
                }
            }

            when {
                callbackCount == 0 -> {
                    val missingResult = IllegalStateException(
                        "Schema query executor completed without delivering an execution result",
                    )
                    ExecuteResult.Failure(scope.recordFailure(missingResult))
                }
                scope.firstFailure != null -> ExecuteResult.Failure(requireNotNull(scope.firstFailure))
                else -> requireNotNull(callbackResult)
            }
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            val primary = scope.firstFailure
            if (primary != null && primary !== failure) addSuppressedSafely(primary, failure)
            ExecuteResult.Failure(scope.recordFailure(failure))
        }
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

    private fun addSuppressedSafely(primary: Throwable, secondary: Throwable) {
        if (primary !== secondary) primary.addSuppressed(secondary)
    }
}
