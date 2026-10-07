package gog.my_project.data_base.query.executer.result

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

class InsertQueryConsumer internal constructor(
    private val query: IQueryRenderInsertApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun execute(): ExecuteResult<Long> = captureMutationResult { callback ->
        executor.execute(query, blockExecute = callback)
    }

    fun sql(): BuiltQuery = BuiltQuery(executor.sql(query), query.params.toMutableList())

    fun sql(block: (BuiltQuery) -> Unit): InsertQueryConsumer {
        block(sql())
        return this
    }
}

class UpdateQueryConsumer internal constructor(
    private val query: IQueryRenderUpdateApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun execute(): ExecuteResult<Int> = captureMutationResult { callback ->
        executor.execute(query, blockExecute = callback)
    }

    fun sql(): BuiltQuery = BuiltQuery(executor.sql(query), query.params.toMutableList())

    fun sql(block: (BuiltQuery) -> Unit): UpdateQueryConsumer {
        block(sql())
        return this
    }
}

class DeleteQueryConsumer internal constructor(
    private val query: IQueryRenderDeleteApi,
    private val executor: IQueryBuilderExecutor,
) {
    fun execute(): ExecuteResult<Int> = captureMutationResult { callback ->
        executor.execute(query, blockExecute = callback)
    }

    fun sql(): BuiltQuery = BuiltQuery(executor.sql(query), query.params.toMutableList())

    fun sql(block: (BuiltQuery) -> Unit): DeleteQueryConsumer {
        block(sql())
        return this
    }
}

private fun <T> captureMutationResult(
    operation: (callback: (ExecuteResult<T>) -> Unit) -> Unit,
): ExecuteResult<T> {
    var result: ExecuteResult<T>? = null
    return try {
        operation { delivered ->
            check(result == null) { "Mutation execution delivered more than one result" }
            result = delivered
        }
        result ?: ExecuteResult.Failure(
            IllegalStateException("Mutation execution did not deliver a synchronous result"),
        )
    } catch (failure: Throwable) {
        ExecuteResult.Failure(failure)
    }
}
