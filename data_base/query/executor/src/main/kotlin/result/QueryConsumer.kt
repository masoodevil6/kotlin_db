package gog.my_project.data_base.query.executer.result

import gog.my_project.collection.Collection
import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadataProvider
import gog.my_project.data_base.query.meta_data.analysis.EffectiveQueryMetadataAnalyzer
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.data_base.query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering

/** Fluent SELECT handle that delegates execution and renders SQL with its parameter occurrences. */
class QueryConsumer internal constructor(
    private val query: IQueryRenderSelectApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun first(): ExecuteResult<QueryRow> = captureSynchronousResult { blockExecute ->
        executor.first(query, blockExecute = blockExecute)
    }

    fun execute(): ExecuteResult<List<QueryRow>> = captureSynchronousResult { blockExecute ->
        executor.get(query, blockExecute = blockExecute)
    }

    /** Executes this SELECT once and maps its materialized rows into a typed collection. */
    fun <T> executeAs(mapper: (QueryRow) -> T): ExecuteResult<ICollection<T>> {
        return when (val execution = execute()) {
            is ExecuteResult.Failure -> execution
            is ExecuteResult.Success -> {
                val rows = execution.result
                    ?: return ExecuteResult.Failure(
                        IllegalStateException("SELECT completed without a materialized row list"),
                    )
                val mappedRows = Collection<T>()
                for ((index, row) in rows.withIndex()) {
                    val mapped = try {
                        mapper(row)
                    } catch (failure: Exception) {
                        return ExecuteResult.Failure(
                            IllegalStateException(
                                "Failed to map SELECT result row ${index + 1}",
                                failure,
                            ),
                        )
                    }
                    mappedRows.add(mapped)
                }
                ExecuteResult.Success(mappedRows)
            }
        }
    }

    fun sql(): BuiltQuery {
        val dialect = DialectSelector().select(DefaultDatabaseConfig.config.dialect)
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
    } catch (failure: Throwable) {
        ExecuteResult.Failure(failure)
    }
}
