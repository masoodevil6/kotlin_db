package gog.my_project.query.interfaces.query_builders.tools.table

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsTable : IQueryTools {

    fun getTableName(): String?;
    fun getTableAlias(): String?;

    fun table(tableName: String?): IQueryToolsTable;
    fun table(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable;

    fun alias(aliasName: String?): IQueryToolsTable;

}