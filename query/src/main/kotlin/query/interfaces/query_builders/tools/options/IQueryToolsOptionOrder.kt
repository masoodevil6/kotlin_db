package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.enums.SqlOrderType
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase


interface IQueryToolsOptionOrder: IQueryTools {

    var params: MutableList<Any?>

    fun getOrderType(): SqlOrderType;
    fun getListColumns(): MutableList<IQueryToolsColumnsBase>;

    fun orderAsc() : IQueryToolsOptionOrder;
    fun orderDesc() : IQueryToolsOptionOrder;

    fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionOrder

}