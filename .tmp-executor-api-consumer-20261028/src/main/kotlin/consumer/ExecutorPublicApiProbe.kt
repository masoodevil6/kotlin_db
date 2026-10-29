package consumer

import gog.my_project.collection.ICollection
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.api.metadata.EffectiveQueryMetadata
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.interfaces.QueryBuilderOperationBlock
import gog.my_project.data_base.query.executer.result.DeleteQueryConsumer
import gog.my_project.data_base.query.executer.result.InsertQueryConsumer
import gog.my_project.data_base.query.executer.result.QueryBuilderSelection
import gog.my_project.data_base.query.executer.result.QueryConsumer
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.UpdateQueryConsumer
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.then
import gog.my_project.data_base.query.executer.result.toCollection
import gog.my_project.data_base.query.executer.result.toList
import java.sql.ResultSet

/** Compile-only surface probe. None of these methods are invoked by compileKotlin. */
class ExecutorPublicApiProbe {
    fun transaction(executor: IQueryBuilderExecutor): ExecuteResult<String> =
        executor.transaction { tx ->
            val typed: ExecuteResult<ICollection<QueryRow>> =
                tx.queryBuilderSelect { }.executeAs { row -> row }
            typed.error { _: Throwable -> }
            ExecuteResult.Success("ok")
        }

    fun connectionScope(
        executor: IQueryBuilderExecutor,
        query: IQueryRenderUpdateApi,
    ): ExecuteResult<Int> = executor.connection { scoped ->
        scoped.queryBuilder(query).execute()
    }

    fun typedSelect(executor: IQueryBuilderExecutor, query: IQueryRenderSelectApi): ExecuteResult<ICollection<QueryRow>> =
        executor.queryBuilder(query).executeAs { row -> row }

    fun rawSelect(executor: IQueryBuilderExecutor, query: IQueryRenderSelectApi): ExecuteResult<List<QueryRow>> =
        executor.queryBuilder(query).execute()

    fun selectConsumer(executor: IQueryBuilderExecutor): QueryConsumer =
        executor.queryBuilderSelect { _: IQueryRenderSelectApi -> }

    fun queryBuilderOperation(executor: IQueryBuilderExecutor): QueryConsumer {
        val block = QueryBuilderOperationBlock<QueryConsumer> { selection: QueryBuilderSelection ->
            selection.querySelect { _: IQueryRenderSelectApi -> }
        }
        return executor.queryBuilder(block)
    }

    fun selectSql(executor: IQueryBuilderExecutor, query: IQueryRenderSelectApi): String =
        executor.sql(query)

    fun consumerSqlAndDetails(consumer: QueryConsumer): Pair<BuiltQuery, EffectiveQueryMetadata> {
        val built: BuiltQuery = consumer.sql()
        consumer.sql { _: BuiltQuery -> }
        val details: EffectiveQueryMetadata = consumer.details()
        consumer.details { _: EffectiveQueryMetadata -> }
        return built to details
    }

    fun rawList(result: ExecuteResult<List<QueryRow>>): List<QueryRow> = result.toList()

    fun typedCollection(result: ExecuteResult<ICollection<QueryRow>>): ICollection<QueryRow> =
        result.toCollection()

    fun typedList(result: ExecuteResult<ICollection<QueryRow>>): List<QueryRow> = result.toList()

    fun fluentResult(result: ExecuteResult<String>): ExecuteResult<Int> =
        result.success { value -> value.length }.error { _: Throwable -> }

    fun flattenResult(result: ExecuteResult<String>): ExecuteResult<Int> =
        result.then { value ->
            if (value == null) ExecuteResult.Failure(IllegalStateException("missing value"))
            else ExecuteResult.Success(value.length)
        }

    fun insertConsumer(executor: IQueryBuilderExecutor, query: IQueryRenderInsertApi): InsertQueryConsumer =
        executor.queryBuilder(query)

    fun updateConsumer(executor: IQueryBuilderExecutor, query: IQueryRenderUpdateApi): UpdateQueryConsumer =
        executor.queryBuilder(query)

    fun deleteConsumer(executor: IQueryBuilderExecutor, query: IQueryRenderDeleteApi): DeleteQueryConsumer =
        executor.queryBuilder(query)

    fun insert(executor: IQueryBuilderExecutor, query: IQueryRenderInsertApi): ExecuteResult<Long> =
        executor.queryBuilder(query).execute()

    fun update(executor: IQueryBuilderExecutor, query: IQueryRenderUpdateApi): ExecuteResult<Int> =
        executor.queryBuilder(query).execute()

    fun delete(executor: IQueryBuilderExecutor, query: IQueryRenderDeleteApi): ExecuteResult<Int> =
        executor.queryBuilder(query).execute()

    fun allDirectExecutions(
        executor: IQueryBuilderExecutor,
        select: IQueryRenderSelectApi,
        insert: IQueryRenderInsertApi,
        update: IQueryRenderUpdateApi,
        delete: IQueryRenderDeleteApi,
    ) {
        executor.first(select, blockExecute = { _: ExecuteResult<QueryRow> -> })
        executor.get(select, blockExecute = { _: ExecuteResult<List<QueryRow>> -> })
        executor.execute(select, blockExecute = { _: ExecuteResult<ResultSet> -> })
        executor.execute(insert, blockExecute = { _: ExecuteResult<Long> -> })
        executor.execute(update, blockExecute = { _: ExecuteResult<Int> -> })
        executor.execute(delete, blockExecute = { _: ExecuteResult<Int> -> })
        executor.sql(select)
        executor.sql(insert)
        executor.sql(update)
        executor.sql(delete)
    }

    fun <Ast : IQueryAst<*>, Api : IQueryApi<Ast>> executeOut(
        executor: IQueryBuilderExecutor,
        query: Api,
        callback: (IQueryExecute, BuiltQuery) -> Unit,
    ) {
        executor.executeOut(query, blockResult = callback)
    }

    fun selectionMethods(selection: QueryBuilderSelection) {
        selection.querySelect { }
        selection.queryInsert { }
        selection.queryUpdate { }
        selection.queryDelete { }
    }

    fun failureType(result: ExecuteResult<*>): Throwable? =
        (result as? ExecuteResult.Failure)?.exception
}
