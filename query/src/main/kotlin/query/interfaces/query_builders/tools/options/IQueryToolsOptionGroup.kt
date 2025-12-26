package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools


interface IQueryToolsOptionGroup : IQueryTools {

    fun groupSetup(blockGroup: (IQueryToolsOptionGroup) -> IQueryToolsOptionGroup) : IQueryToolsOptionGroup;

    fun addColumn(columnName : String): IQueryToolsOptionGroup;

}