package gog.kotlin_db.data.base.QueryBuilder.QuerySelectColumn

import gog.kotlin_db.data.base.QueryBuilder.IQueryBuilderMethods
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder.Companion._QUERY_FUN_AVG
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder.Companion._QUERY_FUN_COUNT
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder.Companion._QUERY_FUN_MAX
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder.Companion._QUERY_FUN_MIN
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder.Companion._QUERY_FUN_SUM
import gog.kotlin_db.data.base.QueryBuilder.QueryColumn.QueryBuilder_column

interface IQueryBuilder_selectColumnMethods : IQueryBuilderMethods  {

    fun column(columnsName: String) : QueryBuilder;
    fun column(columnsName: String , columnAlias: String) : QueryBuilder;
    fun column(columnAlias: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

    fun count(columnsName: String, columnAlias: String = "count") : QueryBuilder;
    fun count(columnAlias: String = "count", block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

    fun sum(columnsName: String, columnAlias: String = "sum") : QueryBuilder;
    fun sum(columnAlias: String = "sum", block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

    fun avg(columnsName: String, columnAlias: String = "avg") : QueryBuilder;
    fun avg(columnAlias: String = "avg" , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

    fun max(columnsName: String, columnAlias: String = "max") : QueryBuilder;
    fun max(columnAlias: String = "max" , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

    fun min(columnsName: String, columnAlias: String = "min") : QueryBuilder;
    fun min(columnAlias: String = "min" , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder;

}