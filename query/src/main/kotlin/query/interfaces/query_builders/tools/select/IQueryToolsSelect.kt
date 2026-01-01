package gog.my_project.query.interfaces.query_builders.tools.select

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns

interface IQueryToolsSelect : IQueryTools {
    fun getListColumns() :MutableList<IQueryToolsColumns>;

    fun addColumn(blockColumn: IQueryToolsColumns.() -> IQueryToolsColumns): IQueryToolsSelect;
}