package gog.my_project.data_base.query.api.interfaces.option_order

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.tools.enums.SqlOrderType
import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst

interface IQueryOptionOrderApi : IQueryApi {

    var ast: IQueryOptionOrderAst;

    fun orderType(type: SqlOrderType) : IQueryOptionOrderApi;
    fun orderAsc() : IQueryOptionOrderApi;
    fun orderDesc() : IQueryOptionOrderApi;

    fun addColumn(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryOptionOrderApi

}