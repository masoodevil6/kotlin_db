package gog.my_project.query.interfaces.query_builders.tools.table

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools

interface IQueryToolsTable : IQueryTools {

    fun getTableName(): String?;
    fun getTableAlias(): String?;
    fun getTableQuery(): IQueryBuilder?;

    fun table(tableName: String?): IQueryToolsTable;
    fun tableQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable;

    fun alias(aliasName: String?): IQueryToolsTable;

}