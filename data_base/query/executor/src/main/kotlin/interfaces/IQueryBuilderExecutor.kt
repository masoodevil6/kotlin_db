package gog.my_project.data_base.query.executer.interfaces

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi

interface IQueryBuilderExecutor {

    fun execute(
        queryBuilder: gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi,
        blockExecute: (ExecuteResult) -> Unit,
        blockQueryInfo: ((query: String? , paramsMap: MutableMap<String , Any?>) -> Unit)? = null
    )

}