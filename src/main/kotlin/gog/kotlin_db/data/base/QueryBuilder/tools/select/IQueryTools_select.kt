package gog.kotlin_db.data.base.QueryBuilder.tools.select

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools

interface IQueryTools_select : IQueryTools {

    fun selectSetup(blockSelect: (IQueryTools_select) -> QueryTools_select): QueryTools_select;

    fun column(columnsName: String) : QueryTools_select;
    fun column(columnsName: String , columnAlias: String) : QueryTools_select;
    fun column(columnAlias: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

    fun count(columnsName: String, columnAlias: String = "count") : QueryTools_select;
    fun count(columnAlias: String = "count", block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

    fun sum(columnsName: String, columnAlias: String = "sum") : QueryTools_select;
    fun sum(columnAlias: String = "sum", block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

    fun avg(columnsName: String, columnAlias: String = "avg") : QueryTools_select;
    fun avg(columnAlias: String = "avg" , block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

    fun max(columnsName: String, columnAlias: String = "max") : QueryTools_select;
    fun max(columnAlias: String = "max" , block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

    fun min(columnsName: String, columnAlias: String = "min") : QueryTools_select;
    fun min(columnAlias: String = "min" , block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

    fun methodFun(methodName: String, columnsName: String, columnAlias: String) : QueryTools_select;
    fun methodFun(methodName: String, columnAlias: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_select;

}