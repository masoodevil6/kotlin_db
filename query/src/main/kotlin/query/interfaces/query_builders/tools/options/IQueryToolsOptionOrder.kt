package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.enums.SqlOrderType
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase


interface IQueryToolsOptionOrder: IQueryTools {

    fun type(orderType : SqlOrderType) : IQueryToolsOptionOrder;
    fun typeAsc() : IQueryToolsOptionOrder;
    fun typeDesc() : IQueryToolsOptionOrder;

    fun addColumn(columnName : String) : IQueryToolsOptionOrder;
    fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionOrder

}