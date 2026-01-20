package gog.my_project.data_base.query_builder.query.interfaces.options

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumnsBase

interface IQueryToolsOptionGroup : IQueryTools {

    fun getListColumns(): MutableList<IQueryToolsColumnsBase>;

    fun addColumn(blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsOptionGroup

}