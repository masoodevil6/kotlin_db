package gog.my_project.data_base_query.executor.result

import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base_query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base_query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base_query.api.metadata.EffectiveQueryMetadataProvider
import gog.my_project.data_base_query.metadata.analysis.EffectiveQueryMetadataAnalyzer
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base_query.executor.manager.recordScopedConsumerThrowable
import gog.my_project.data_base_query.renderer.manager.DialectSelector
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering

/** Fluent SELECT handle that delegates execution and renders SQL with its parameter occurrences. */
class QueryConsumer internal constructor(
    private val query: IQueryRenderSelectApi,
    private val executor: IQueryBuilderExecutor,
    private val reportScopedThrowable: (Throwable) -> Unit = { failure ->
        recordScopedConsumerThrowable(executor, failure)
    },
) {
    fun first(): ExecuteResult<QueryRow> = captureSynchronousResult { blockExecute ->
        executor.first(query, blockExecute = blockExecute)
    }

    fun execute(): ExecuteResult<List<QueryRow>> = captureSynchronousResult { blockExecute ->
        executor.get(query, blockExecute = blockExecute)
    }

    /** Executes this SELECT once and maps its materialized rows into a typed collection. */
    fun <T> executeAs(mapper: (QueryRow) -> T): ExecuteResult<ICollection<T>> {
        var result: ExecuteResult<ICollection<T>>? = null
        return try {
            executor.get(query, blockExecute = { execution ->
                result = when (execution) {
                    is ExecuteResult.Failure -> execution
                    is ExecuteResult.Success -> {
                        val rows = execution.result
                            ?: throw IllegalStateException("SELECT completed without a materialized row list")
                        val mappedRows = Collection<T>()
                        for ((index, row) in rows.withIndex()) {
                            val mapped = try {
                                mapper(row)
                            } catch (failure: Exception) {
                                throw IllegalStateException(
                                    "Failed to map SELECT result row ${index + 1}",
                                    failure,
                                )
                            }
                            mappedRows.add(mapped)
                        }
                        ExecuteResult.Success(mappedRows)
                    }
                }
            })
            result ?: ExecuteResult.Failure(
                IllegalStateException("SELECT execution did not deliver a synchronous result"),
            ).also { reportScopedThrowable(it.exception) }
        } catch (error: Error) {
            // QueryBuilderExecutor records this as fatal while the Operation scope is still active.
            throw error
        } catch (failure: Throwable) {
            if (executor !is gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor) {
                reportScopedThrowable(failure)
            }
            ExecuteResult.Failure(failure)
        }
    }

    fun sql(): BuiltQuery {
        val dialect = DialectSelector().select(executor.databasePlatformConfig.dialect)
        return when (val rendered = QueryParameterRendering.render(query.ast, dialect)) {
            is LegacyQueryRenderResult -> BuiltQuery(
                requireNotNull(rendered.sql) { "rendered sql is null" },
                mutableListOf(),
            )
            is IdentityAwareQueryRenderResult -> BuiltQuery(
                rendered.displaySql,
                rendered.parameterOccurrences.toMutableList(),
            )
        }
    }

    fun sql(block: (BuiltQuery) -> Unit): QueryConsumer {
        block(sql())
        return this
    }

    fun details(): EffectiveQueryMetadata =
        (query as? EffectiveQueryMetadataProvider)?.effectiveQueryMetadata()
            ?: EffectiveQueryMetadataAnalyzer().analyze(query.ast)

    fun details(block: (EffectiveQueryMetadata) -> Unit): QueryConsumer {
        block(details())
        return this
    }
}

private fun <T> captureSynchronousResult(
    operation: (blockExecute: (ExecuteResult<T>) -> Unit) -> Unit,
): ExecuteResult<T> {
    var result: ExecuteResult<T>? = null
    return try {
        operation { delivered ->
            check(result == null) { "SELECT execution delivered more than one result" }
            result = delivered
        }
        result ?: ExecuteResult.Failure(
            IllegalStateException("SELECT execution did not deliver a synchronous result"),
        )
    } catch (error: Error) {
        throw error
    } catch (failure: Throwable) {
        ExecuteResult.Failure(failure)
    }
}
