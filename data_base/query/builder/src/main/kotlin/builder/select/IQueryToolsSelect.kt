package gog.my_project.data_base.query.builder.interfaces.select

import gog.my_project.data_base.query.builder.interfaces.IQueryTools
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumns

interface IQueryToolsSelect : IQueryTools {

    fun getListColumns() :MutableList<IQueryToolsColumns>;

}