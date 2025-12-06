package gog.kotlin_db.data.base.QueryBuilder.QueryTable

import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilderMethods
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

interface IQueryBuilder_tableMethods : IQueryBuilderMethods {

    fun table(tableName: String?): QueryBuilder;
    fun table(tableName: String? , aliasName: String?): QueryBuilder;
    fun table(aliasName: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

}