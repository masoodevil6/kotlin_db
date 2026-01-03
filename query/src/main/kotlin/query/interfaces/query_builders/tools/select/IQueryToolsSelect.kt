package gog.my_project.query.interfaces.query_builders.tools.select

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.columns.IQueryToolsColumns

interface IQueryToolsSelect : IQueryTools {

    var params: MutableList<Any?>

    fun getListColumns() :MutableList<IQueryToolsColumns>;

    fun addColumn(blockColumn: IQueryToolsColumns.() -> IQueryToolsColumns): IQueryToolsSelect;
}