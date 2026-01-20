package gog.my_project.data_base.query_builder.query.interfaces.table

import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools

interface IQueryToolsTable : IQueryTools {

    fun getTableName(): String?;
    fun getTableAlias(): String?;
    fun getTableQuery(): IQueryBuilder?;

    fun table(tableName: String?): IQueryToolsTable;
    fun tableQuery(block: IQueryBuilder.() -> IQueryBuilder): IQueryToolsTable;

    fun alias(aliasName: String?): IQueryToolsTable;

}