package gog.kotlin_db.data.base.QueryBuilder.tools.select

import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder
import gog.kotlin_db.data.base.QueryBuilder.tools.column.QueryTools_columns

class QueryTools_select(

) : IQueryTools_select {

    protected var columns: MutableList<QueryTools_columns> = mutableListOf()


    companion object {
        const val _TAG_TEMP_SELECT=             "{{_TAG_TEMP_SELECT}}"
        const val _TAG_TEMP_SELECT_COLUMNS=     "{{_TAG_TEMP_SELECT_COLUMNS}}"

        /*----------------
          Methods
        ----------------*/
        const val _QUERY_FUN_COUNT =  "count"
        const val _QUERY_FUN_SUM =    "sum"
        const val _QUERY_FUN_AVG =    "avg"
        const val _QUERY_FUN_MAX =    "max"
        const val _QUERY_FUN_MIN =    "min"
    }







    override fun selectSetup(blockSelect: (IQueryTools_select) -> QueryTools_select): QueryTools_select {
        return  blockSelect(QueryTools_select());
    }

    override fun column(columnsName: String): QueryTools_select {
        val column = QueryTools_columns(columnsName);
        columns?.add(column);
        return this;
    }

    override fun column(
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        val column = QueryTools_columns(columnsName , columnAlias);
        columns?.add(column);
        return this;
    }

    override fun column(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        val columnsName = block(QueryBuilder());
        val column = QueryTools_columns(columnsName.toSql().toString() , columnAlias);
        columns?.add(column);
        return this;
    }

    override fun count(
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_COUNT , columnsName , columnAlias);
    }

    override fun count(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_COUNT, columnAlias , block);
    }

    override fun sum(
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_SUM, columnsName , columnAlias);
    }

    override fun sum(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_SUM, columnAlias , block);
    }

    override fun avg(
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_AVG, columnsName , columnAlias);
    }

    override fun avg(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_AVG, columnAlias , block);
    }

    override fun max(
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_MAX, columnsName , columnAlias);
    }

    override fun max(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_MAX, columnAlias , block);
    }

    override fun min(
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_MIN, columnsName , columnAlias);
    }

    override fun min(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        return methodFun(_QUERY_FUN_MIN, columnAlias , block);
    }

    override fun methodFun(
        methodName: String,
        columnsName: String,
        columnAlias: String
    ): QueryTools_select {
        val column = QueryTools_columns(columnsName , columnAlias ,methodName);
        columns?.add(column);
        return this;
    }

    override fun methodFun(
        methodName: String,
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryTools_select {
        val columnsName = block(QueryBuilder());
        val column = QueryTools_columns(columnsName.toSql().toString() , columnAlias ,methodName);
        columns?.add(column);
        return this;
    }










    override fun getBaseTempSql(): String? {
        return " select $_TAG_TEMP_SELECT_COLUMNS " ;
    }

    override fun toSql(): String? {
        if (columns != null && columns.size > 0) {
            var queryTemp = getBaseTempSql();

            var selects = "";
            for ((index, column) in columns.withIndex()){
                selects +=  "\n ${column.toSql()}";
                if (index < columns.size - 1){
                    selects += ","
                }
            }
            return queryTemp?.replace(_TAG_TEMP_SELECT_COLUMNS, selects);
        }
        return null;
    }

    override fun replaceInBaseTemp(query: String): String {
        val querySelect= toSql();
        return query.replace(_TAG_TEMP_SELECT, querySelect ?: "");
    }


}