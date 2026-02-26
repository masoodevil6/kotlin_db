package gog.my_project.data_base.query.executer.interfaces

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet

interface IQueryBuilderExecutor  {

    //// select
    fun execute(
        queryBuilder:    IQueryRenderSelectApi,
        blockExecute:    (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    //// insert
    fun execute(
        queryBuilder:     IQueryRenderInsertApi,
        blockExecute:     (ExecuteResult<Long>) -> Unit,
        blockQueryInfo:   ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    //// update
    fun execute(
        queryBuilder:     IQueryRenderUpdateApi,
        blockExecute:     (ExecuteResult<Int>) -> Unit,
        blockQueryInfo:   ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

    //// update
    fun execute(
        queryBuilder:     IQueryRenderDeleteApi,
        blockExecute:     (ExecuteResult<Int>) -> Unit,
        blockQueryInfo:   ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )


    fun <Ast: IQueryAst,Api :IQueryApi<Ast> > executeOut(
        queryBuilder:    Api,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null,
        blockResult:     (db: IQueryExecute ,builtQuery: BuiltQuery) -> Unit
    ){
        val db = QueryExecute()

        val dialect  = DialectSelector().select(DefaultDatabaseConfig.dialect)

        val query = dialect.render(queryBuilder.ast) ?: throw IllegalStateException("rendered sql is null")

        val params: MutableList<SqlParameter<*>> = queryBuilder.params;

        if (blockQueryInfo != null){
            val paramsMap: MutableMap<String , Any?> = params.associate { it -> it.name to it.value }.toMutableMap()
            val queryString = StringTools.formatSql(query)

            blockQueryInfo(queryString , paramsMap)
        }

        blockResult(db , BuiltQuery(query, params));
    }

}