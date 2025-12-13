package gog.kotlin_db.data.base.query_builder.tools.table

import gog.kotlin_db.data.base.query_builder.QueryBuilder
import gog.kotlin_db.data.base.query_builder.tools.IQueryTools

interface IQueryToolsTable : IQueryTools {


    fun tableSetup(blockTable: (IQueryToolsTable) -> QueryToolsTable): QueryToolsTable;

    fun table(tableName: String?): QueryToolsTable;
    fun table(tableName: String? , aliasName: String?): QueryToolsTable;
    fun table(aliasName: String , block: (QueryBuilder) -> QueryBuilder) : QueryToolsTable;


}