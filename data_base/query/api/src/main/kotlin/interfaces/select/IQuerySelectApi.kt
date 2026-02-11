package gog.my_project.data_base.query.api.interfaces.select

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.column.IQueryColumnsApi
import gog.my_project.data_base.query.ast.interfaces.select.IQuerySelectAst

interface IQuerySelectApi : IQueryApi {

    var ast: IQuerySelectAst;

    fun addColumn(blockColumn: IQueryColumnsApi.() -> Unit): IQuerySelectApi;

}