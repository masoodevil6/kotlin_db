package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsOptionOrder: IQueryTools {

    fun orderSetup(blockOrder: (IQueryToolsOptionOrder) -> IQueryToolsOptionOrder) : IQueryToolsOptionOrder;

    fun addColumn(columnName : String) : IQueryToolsOptionOrder;
    fun type(orderType : String) : IQueryToolsOptionOrder;

}