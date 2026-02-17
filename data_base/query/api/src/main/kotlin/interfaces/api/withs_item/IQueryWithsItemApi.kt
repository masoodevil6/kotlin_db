package gog.my_project.data_base.query.api.interfaces.api.withs_item

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.ast.interfaces.withs_item.IQueryWithsItemAst

interface IQueryWithsItemApi : IQueryApi {

    var ast: IQueryWithsItemAst;

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