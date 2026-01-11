package gog.my_project.query.interfaces.query_builders.tools.options

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumnsBase

interface IQueryToolsOptionGroup : IQueryTools {

    fun getListColumns(): MutableList<IQueryToolsColumnsBase>;

    fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionGroup

}