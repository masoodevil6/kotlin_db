package gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst

interface IQueryWithsItemApi : IQueryApi<IQueryWithsItemAst> {

    //fun with(with : ICte): IQueryWithsItemApi;

    fun with(
        withName: String,
        withClass : ICte
    ): IQueryWithsItemApi;

    fun with(
        withName: String,
        blockWith: IQueryRenderSelectApi.() -> Unit
    ): IQueryWithsItemApi;

}