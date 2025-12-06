package gog.kotlin_db.data.base.QueryBuilder

import gog.kotlin_db.data.base.QueryBuilder.Conditions.QueryConditionGroups.QueryBuilder_conditionsGroup
import gog.kotlin_db.data.base.QueryBuilder.QueryColumn.QueryBuilder_column
import gog.kotlin_db.data.base.QueryBuilder.QuerySelectColumn.IQueryBuilder_selectColumnMethods
import gog.kotlin_db.data.base.QueryBuilder.QuerySelectColumn.QueryBuilder_selectColumn
import gog.kotlin_db.data.base.QueryBuilder.QueryTable.IQueryBuilder_tableMethods
import gog.kotlin_db.data.base.QueryBuilder.QueryTable.QueryBuilder_table
import gog.kotlin_db.data.base.QueryBuilder.QueryWhere.IQueryBuilder_whereMethods
import gog.kotlin_db.data.base.QueryBuilder.QueryWhere.QueryBuilder_where

class QueryBuilder(

) : IQueryBuilder_selectColumnMethods  ,
    IQueryBuilder_tableMethods ,
    IQueryBuilder_whereMethods,
    IQueryBuilder
{


    companion object {

        /*----------------
          Templates
        ----------------*/
        const val _TAG_TEMP_SELECT =    "{{_TAG_TEMP_COLUMNS}}"
        const val _TAG_TEMP_TABLE=      "{{_TAG_TEMP_TABLE}}"
        const val _TAG_TEMP_JOINS=      "{{_TAG_TEMP_JOINS}}"
        const val _TAG_TEMP_WHERES=     "{{_TAG_TEMP_WHERES}}"
        const val _TAG_TEMP_OPTIONS=    "{{_TAG_TEMP_OPTIONS}}"


        /*----------------
           Methods
        ----------------*/
        const val _QUERY_FUN_COUNT =  "count"
        const val _QUERY_FUN_SUM =    "sum"
        const val _QUERY_FUN_AVG =    "avg"
        const val _QUERY_FUN_MAX =    "max"
        const val _QUERY_FUN_MIN =    "min"


        /*----------------
           Logical
        ----------------*/
        const val _LOGICAL_AND =   "and"
        const val _LOGICAL_ON =    "on"
        const val _LOGICAL_OR  =   "or"


        /*----------------
           Operation
        ----------------*/
        const val _OPERATION_EQUALS =               "="
        const val _OPERATION_NOT_EQUALS =           "<>"
        const val _OPERATION_GEATER_THAN =          ">"
        const val _OPERATION_LESS_THAN =            "<"
        const val _OPERATION_GEATER_OR_EQUAL_THAN = ">="
        const val _OPERATION_LESS_OR_EQUAL_THAN =   "<="
        const val _OPERATION_LIKE =                 "like"
        const val _OPERATION_IN =                   "IN"
        const val _OPERATION_BETWEEN =              "between"



    }




    var _queryBuilderSelect : QueryBuilder_selectColumn? = QueryBuilder_selectColumn();
    var _queryBuilderTable : QueryBuilder_table? = QueryBuilder_table();
    var _queryBuilderWhereConditions : QueryBuilder_where? = QueryBuilder_where();






    override fun setTemplatePartQuery(queryTemp: String?): String? {
        var temp = queryTemp;
        temp =  temp?.replace(_TAG_TEMP_SELECT, _queryBuilderSelect?.toSql() ?: "");
        temp =  temp?.replace(_TAG_TEMP_TABLE, _queryBuilderTable?.toSql() ?: "");
        temp =  temp?.replace(_TAG_TEMP_WHERES, _queryBuilderWhereConditions?.toSql() ?: "");
        return  temp;
    }





    /* ------------------------------------
    Builder
    ------------------------------------ */

    override fun getBaseTempSql(): String? {
        return "$_TAG_TEMP_SELECT $_TAG_TEMP_TABLE $_TAG_TEMP_JOINS $_TAG_TEMP_WHERES  $_TAG_TEMP_OPTIONS ";
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp = setTemplatePartQuery(queryTemp);
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return query ;
    }








    /* ------------------------------------
       Select
    ----------------------------------- */

    override fun column(columnsName: String): QueryBuilder {
        val column = QueryBuilder_column(columnsName);
        _queryBuilderSelect?.addToSelectColumn(column);
        return this;
    }

    override fun column(
        columnsName: String,
        columnAlias: String
    ): QueryBuilder {
        val column = QueryBuilder_column(columnsName , columnAlias);
        _queryBuilderSelect?.addToSelectColumn(column);
        return this;
    }

    override fun column(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        val columnsName = block(QueryBuilder());
        val column = QueryBuilder_column(columnsName.toSql().toString() , columnAlias);
        _queryBuilderSelect?.addToSelectColumn(column);
        return this;
    }

    override fun count(
        columnsName: String,
        columnAlias: String
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_COUNT , columnsName , columnAlias);
    }

    override fun count(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_COUNT , columnAlias , block);
    }

    override fun sum(
        columnsName: String,
        columnAlias: String
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_SUM , columnsName , columnAlias);
    }

    override fun sum(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_SUM , columnAlias , block);
    }

    override fun avg(
        columnsName: String,
        columnAlias: String
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_AVG , columnsName , columnAlias);
    }

    override fun avg(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_AVG , columnAlias , block);
    }

    override fun max(
        columnsName: String,
        columnAlias: String
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_MAX , columnsName , columnAlias);
    }

    override fun max(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_MAX , columnAlias , block);
    }

    override fun min(
        columnsName: String,
        columnAlias: String
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_MIN , columnsName , columnAlias);
    }

    override fun min(
        columnAlias: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        return methodFun(_QUERY_FUN_MIN , columnAlias , block);
    }


    private fun methodFun(methodName: String, columnsName: String, columnAlias: String) : QueryBuilder {
        val column = QueryBuilder_column(columnsName , columnAlias ,methodName);
        _queryBuilderSelect?.addToSelectColumn(column);
        return this;
    }
    private fun methodFun(methodName: String, columnAlias: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder {
        val columnsName = block(QueryBuilder());
        val column = QueryBuilder_column(columnsName.toSql().toString() , columnAlias ,methodName);
        _queryBuilderSelect?.addToSelectColumn(column);
        return this;
    }





    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(tableName: String?): QueryBuilder {
        _queryBuilderTable?.tableName = tableName;
        return this;
    }

    override fun table(
        tableName: String?,
        aliasName: String?
    ): QueryBuilder {
        _queryBuilderTable?.tableName = tableName;
        _queryBuilderTable?.aliasName = aliasName;
        return this;
    }

    override fun table(
        aliasName: String,
        block: (QueryBuilder) -> QueryBuilder
    ): QueryBuilder {
        val columnsName = block(QueryBuilder());
        _queryBuilderTable?.tableName = "(${columnsName.toSql().toString()})";
        _queryBuilderTable?.aliasName = aliasName;
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */

    override fun where(blockGroup: (QueryBuilder_conditionsGroup) -> QueryBuilder_conditionsGroup): QueryBuilder {
        val conditionGroup = blockGroup(QueryBuilder_conditionsGroup(_LOGICAL_AND))
        _queryBuilderWhereConditions?.condition = conditionGroup;
        return this;
    }





}