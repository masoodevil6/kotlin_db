package gog.my_project.data_base.query.builder.interfaces.options

import gog.my_project.data_base.query.builder.interfaces.IQueryTools
import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase

interface IQueryToolsOptionGroup :  IQueryTools {

    fun getListColumns(): MutableList<IQueryToolsColumnsBase>;

}