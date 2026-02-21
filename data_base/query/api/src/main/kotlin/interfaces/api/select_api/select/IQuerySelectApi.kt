package gog.my_project.data_base.query.api.interfaces.api.select_api.select

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst

interface IQuerySelectApi : IQueryApi<IQuerySelectAst> {

    fun addColumn(blockColumn: IQueryColumnsApi.() -> Unit): IQuerySelectApi;

}