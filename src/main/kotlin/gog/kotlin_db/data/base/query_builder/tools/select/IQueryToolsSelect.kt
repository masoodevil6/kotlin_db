package gog.kotlin_db.data.base.query_builder.tools.select

import gog.kotlin_db.data.base.query_builder.QueryBuilder
import gog.kotlin_db.data.base.query_builder.tools.IQueryTools
import kotlin.reflect.KProperty

interface IQueryToolsSelect : IQueryTools {

    fun selectSetup(blockSelect: (IQueryToolsSelect) -> QueryToolsSelect): QueryToolsSelect;

    fun column(columnsName: String) : QueryToolsSelect;
    fun column(columnsName: String , columnAlias: String) : QueryToolsSelect;
    fun column(columnAlias: String , block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

    fun column(prop: KProperty<*>) : IQueryToolsSelect;

    fun count(columnsName: String, columnAlias: String = "count") : QueryToolsSelect;
    fun count(columnAlias: String = "count", block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

    fun sum(columnsName: String, columnAlias: String = "sum") : QueryToolsSelect;
    fun sum(columnAlias: String = "sum", block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

    fun avg(columnsName: String, columnAlias: String = "avg") : QueryToolsSelect;
    fun avg(columnAlias: String = "avg" , block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

    fun max(columnsName: String, columnAlias: String = "max") : QueryToolsSelect;
    fun max(columnAlias: String = "max" , block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

    fun min(columnsName: String, columnAlias: String = "min") : QueryToolsSelect;
    fun min(columnAlias: String = "min" , block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

    fun methodFun(methodName: String, columnsName: String, columnAlias: String) : QueryToolsSelect;
    fun methodFun(methodName: String, columnAlias: String , block: (QueryBuilder) -> QueryBuilder) : QueryToolsSelect;

}