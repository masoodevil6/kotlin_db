package gog.my_project.data_base.query_builder.query.interfaces.select

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import gog.my_project.data_base.query_builder.query.interfaces.columns.IQueryToolsColumns

interface IQueryToolsSelect : IQueryTools {

    fun getListColumns() :MutableList<IQueryToolsColumns>;

    fun addColumn(blockColumn: IQueryToolsColumns.() -> IQueryToolsColumns): IQueryToolsSelect;
}