package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase

interface IQueryToolsOptionGroup : IQueryTools {

    fun addColumn(columnName : String): IQueryToolsOptionGroup;

    fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionGroup
}