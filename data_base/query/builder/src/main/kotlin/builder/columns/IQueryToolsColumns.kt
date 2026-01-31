package gog.my_project.data_base.query.builder.interfaces.columns

import gog.my_project.data_base.query.builder.interfaces.IQueryTools
import gog.my_project.data_base.query.definition.tools.enums.SqlMethodColumn

interface IQueryToolsColumns : IQueryTools {

    fun getColumnMethod(): SqlMethodColumn?;
    fun getColumnName():   IQueryToolsColumnsBase?;
    fun getColumnAlias():  String?;

}