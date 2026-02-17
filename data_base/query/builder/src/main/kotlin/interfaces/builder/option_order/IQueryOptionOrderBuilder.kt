package gog.my_project.data_base.query.builder.interfaces.builder.option_order

import gog.my_project.data_base.query.api.tools.enums.SqlOrderType
import gog.my_project.data_base.query.ast.interfaces.option_order.IQueryOptionOrderAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import gog.my_project.data_base.query.builder.interfaces.builder.column_base.IQueryColumnsBaseBuilder

interface IQueryOptionOrderBuilder : IQuery {

    var ast: IQueryOptionOrderAst;

    fun orderType(type: SqlOrderType) : IQueryOptionOrderBuilder;
    fun orderAsc() : IQueryOptionOrderBuilder;
    fun orderDesc() : IQueryOptionOrderBuilder;

    fun addColumn(blockColumn: IQueryColumnsBaseBuilder.() -> Unit): IQueryOptionOrderBuilder

}