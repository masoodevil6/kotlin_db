package gog.my_project.query.interfaces.query_builders.tools.columns

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsColumns : IQueryTools  {
    fun getColumnMethod(): String?;
    fun getColumnName(): String?;
    fun getColumnAlias(): String?;

    fun sum() : IQueryToolsColumns;
    fun count() : IQueryToolsColumns;
    fun avg() : IQueryToolsColumns;
    fun min() : IQueryToolsColumns;
    fun max() : IQueryToolsColumns;

    fun column( columnName: String): IQueryToolsColumns;
    fun column( blockColumn: IQueryToolsColumnsBase.() -> IQueryToolsColumnsBase): IQueryToolsColumns;
    fun columnQuery( blockQuery: IQueryBuilder.() -> IQueryBuilder): IQueryToolsColumns;

    fun alias(alias: String): IQueryToolsColumns;

}