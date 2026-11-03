package gog.my_project.data_base_query.api.interfaces.cte

import gog.my_project.data_base_query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi

interface ICte {

    fun cteQuery(): IQueryRenderSelectApi

}