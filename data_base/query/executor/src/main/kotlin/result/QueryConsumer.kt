package gog.my_project.data_base.query.executer.result

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

/** Fluent SELECT handle that delegates all work to its bound executor. */
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

    fun sql(): BuiltQuery = BuiltQuery(executor.sql(query), query.params.toMutableList())

    fun sql(block: (BuiltQuery) -> Unit): QueryConsumer {
        block(sql())
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
