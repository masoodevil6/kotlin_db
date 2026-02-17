package gog.my_project.data_base.query.api.interfaces.api.withs

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.ast.interfaces.withs.IQueryWithsAst

interface IQueryWithsApi : IQueryApi {

    var ast: IQueryWithsAst;

    fun addWith(blockWith: IQueryWithsItemApi.() -> Unit) : IQueryWithsApi;

}