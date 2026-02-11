package gog.my_project.data_base.query.api.interfaces.withs

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst

interface IQueryWithsApi : IQueryApi {

    var ast: IQueryWithsAst;

    fun addWith(blockWith: IQueryWithsItemApi.() -> Unit) : IQueryWithsApi;

}