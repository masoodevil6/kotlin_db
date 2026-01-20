package gog.my_project.data_base.query_builder.query.interfaces.columns

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import gog.my_project.data_base.query_builder.query.tools.enums.SqlMethodColumn

interface IQueryToolsColumns : IQueryTools {

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