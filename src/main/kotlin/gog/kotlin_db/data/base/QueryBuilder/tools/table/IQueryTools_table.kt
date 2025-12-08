package gog.kotlin_db.data.base.QueryBuilder.tools.table

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_table : IQueryTools {


    fun tableSetup(blockTable: (IQueryTools_table) -> QueryTools_table): QueryTools_table;

    fun table(tableName: String?): QueryTools_table;
    fun table(tableName: String? , aliasName: String?): QueryTools_table;
    fun table(aliasName: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_table;


}