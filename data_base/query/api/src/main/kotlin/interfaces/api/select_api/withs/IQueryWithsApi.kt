package gog.my_project.data_base.query.api.interfaces.api.select_api.withs

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst

interface IQueryWithsApi : IQueryApi<IQueryWithsAst> {

    fun addWith(blockWith: IQueryWithsItemApi.() -> Unit) : IQueryWithsApi;

}