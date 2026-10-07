package gog.my_project.data_base.query.executer.result

import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor

/** Creates one operation-specific builder and binds it to the receiving executor. */
class QueryBuilderSelection internal constructor(
    private val executor: IQueryBuilderExecutor,
) {
    fun querySelect(block: (IQueryRenderSelectApi) -> Unit): QueryConsumer {
        val query = QueryRenderSelectBuilder()
        block(query)
        return QueryConsumer(query, executor)
    }

    fun queryInsert(block: IQueryRenderInsertApi.() -> Unit): InsertQueryConsumer {
        val query = QueryRenderInsertBuilder()
        query.block()
        return InsertQueryConsumer(query, executor)
    }

    fun queryUpdate(block: IQueryRenderUpdateApi.() -> Unit): UpdateQueryConsumer {
        val query = QueryRenderUpdateBuilder()
        query.block()
        return UpdateQueryConsumer(query, executor)
    }

    fun queryDelete(block: IQueryRenderDeleteApi.() -> Unit): DeleteQueryConsumer {
        val query = QueryRenderDeleteBuilder()
        query.block()
        return DeleteQueryConsumer(query, executor)
    }

    /** Compatibility aliases for the operation names used before the query-prefixed API. */
    fun select(block: (IQueryRenderSelectApi) -> Unit): QueryConsumer = querySelect(block)

    fun insert(block: IQueryRenderInsertApi.() -> Unit): InsertQueryConsumer = queryInsert(block)

    fun update(block: IQueryRenderUpdateApi.() -> Unit): UpdateQueryConsumer = queryUpdate(block)

    fun delete(block: IQueryRenderDeleteApi.() -> Unit): DeleteQueryConsumer = queryDelete(block)
}
