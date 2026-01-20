package gog.my_project.data_base.query_builder.query.interfaces.options

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query_builder.query.tools.enums.SqlOrderType

interface IQueryToolsOptionOrder: IQueryTools {

    fun getOrderType(): SqlOrderType;
    fun getListColumns(): MutableList<IQueryToolsColumnsBase>;

    fun orderAsc() : IQueryToolsOptionOrder;
    fun orderDesc() : IQueryToolsOptionOrder;

    fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionOrder

}