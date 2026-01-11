package gog.my_project.query.interfaces.query_builders.tools.columns

import gog.my_project.datas.SqlParameter
import gog.my_project.enums.SqlMethodColumn
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsColumns : IQueryTools  {

    fun getColumnMethod(): SqlMethodColumn?;
    fun getColumnName():   IQueryToolsColumnsBase?;
    fun getColumnAlias():  String?;

    fun method(method: SqlMethodColumn) : IQueryToolsColumns;
    fun sum() : IQueryToolsColumns;
    fun count() : IQueryToolsColumns;
    fun avg() : IQueryToolsColumns;
    fun min() : IQueryToolsColumns;
    fun max() : IQueryToolsColumns;

    fun column( blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsColumns;

    fun alias(alias: String): IQueryToolsColumns;

}