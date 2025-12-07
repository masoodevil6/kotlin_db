package gog.kotlin_db.data.base.QueryBuilder

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroup
import gog.kotlin_db.data.base.QueryBuilder.tools.options.IQueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.select.IQueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.select.QueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.table.IQueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.table.QueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.where.IQueryTools_where
import gog.kotlin_db.data.base.QueryBuilder.tools.where.QueryTools_where


class QueryBuilder(

) :
    IQueryBuilder
{


    companion object {

        /*----------------
          Templates
        ----------------*/
        const val _TAG_TEMP_JOINS=      "{{_TAG_TEMP_JOINS}}"


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




    //var _queryBuilderSelect : QueryBuilder_selectColumn? = QueryBuilder_selectColumn();



    var _queryBuilderSelect : IQueryTools_select? = QueryTools_select();
    var _queryBuilderTable : IQueryTools_table? = QueryTools_table();
    var _queryBuilderWhere : IQueryTools_where? = QueryTools_where();
    var _queryBuilderOptions : IQueryTools_options? = QueryTools_options();






    private fun setTemplatePartQuery(queryTemp: String?): String? {
        var temp = queryTemp;
        temp =  temp?.replace(QueryTools_select._TAG_TEMP_SELECT, _queryBuilderSelect?.toSql() ?: "");
        temp =  temp?.replace(QueryTools_table._TAG_TEMP_TABLES, _queryBuilderTable?.toSql() ?: "");
        temp =  temp?.replace(QueryTools_where._TAG_TEMP_WHERES, _queryBuilderWhere?.toSql() ?: "");
        temp =  temp?.replace(QueryTools_options._TAG_TEMP_OPTION, _queryBuilderOptions?.toSql() ?: "");
        return  temp;
    }





    /* ------------------------------------
    Builder
    ------------------------------------ */

    override fun getBaseTempSql(): String? {
        return "${QueryTools_select._TAG_TEMP_SELECT} ${QueryTools_table._TAG_TEMP_TABLES} $_TAG_TEMP_JOINS ${QueryTools_where._TAG_TEMP_WHERES}  ${QueryTools_options._TAG_TEMP_OPTION} ";
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
    override fun select(blockSelect: (IQueryTools_select) -> QueryTools_select): QueryBuilder {
        _queryBuilderSelect = _queryBuilderSelect?.selectSetup(blockSelect)
        return this;
    }


    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(blockTable: (IQueryTools_table) -> QueryTools_table): QueryBuilder {
        _queryBuilderTable = _queryBuilderTable?.tableSetup(blockTable)
        return this;
    }


    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: (QueryTools_conditionsGroup) -> QueryTools_conditionsGroup): QueryBuilder {
        _queryBuilderWhere = _queryBuilderWhere?.whereSetup(blockGroup)
        return this;
    }


    /* ------------------------------------
      Options
    ----------------------------------- */
    override fun options(blockGroup: (IQueryTools_options) -> QueryTools_options): QueryBuilder {
        _queryBuilderOptions = _queryBuilderOptions?.optionsSetup(blockGroup)
        return this;
    }




}