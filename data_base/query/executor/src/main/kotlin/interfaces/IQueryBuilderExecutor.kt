package gog.my_project.data_base.query.executer.interfaces

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.ast.interfaces.IQueryAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.renderer.manager.DialectSelector
import gog.my_project.tools.scripts.StringTools

interface IQueryBuilderExecutor  {

    fun execute(
        queryBuilder:    IQueryRenderSelectApi,
        blockExecute:    (ExecuteResult) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )



    fun <Ast: IQueryAst,Api :IQueryApi<Ast>> executeOut(
        queryBuilder:    Api,
        blockExecute:    (ExecuteResult) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
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

        db.execute(
            builtQuery = BuiltQuery(query, params),
            blockExecute = blockExecute
        )
    }


    /*fun execute(
        queryBuilder:    Api,
        blockExecute:    (ExecuteResult) -> Unit,
        blockQueryInfo:  ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )*/

}