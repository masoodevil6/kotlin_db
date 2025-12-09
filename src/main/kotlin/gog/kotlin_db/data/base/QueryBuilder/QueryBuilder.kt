package gog.kotlin_db.data.base.QueryBuilder

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroups
import gog.kotlin_db.data.base.QueryBuilder.tools.join.IQueryTools_joinsConnect
import gog.kotlin_db.data.base.QueryBuilder.tools.join.QueryTools_joinsConnect
import gog.kotlin_db.data.base.QueryBuilder.tools.options.IQueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.select.IQueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.select.QueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.table.IQueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.table.QueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.where.IQueryTools_where
import gog.kotlin_db.data.base.QueryBuilder.tools.where.QueryTools_where
import gog.kotlin_db.data.base.QueryBuilder.tools.with.IQueryTools_withsCollection
import gog.kotlin_db.data.base.QueryBuilder.tools.with.QueryTools_withsCollection
import gog.kotlin_db.utils.tools.StringTools


class QueryBuilder(

) :
    IQueryBuilder
{


    companion object {

    }



    var _queryBuilderWiths : IQueryTools_withsCollection? = QueryTools_withsCollection();
    var _queryBuilderSelect : IQueryTools_select? = QueryTools_select();
    var _queryBuilderTable : IQueryTools_table? = QueryTools_table();
    var _queryBuilderJoins : IQueryTools_joinsConnect? = QueryTools_joinsConnect();
    var _queryBuilderWhere : IQueryTools_where? = QueryTools_where();
    var _queryBuilderOptions : IQueryTools_options? = QueryTools_options();




    private fun setTemplatePartQuery(queryTemp: String?): String? {
        var temp = queryTemp;
        temp =  temp?.replace(QueryTools_withsCollection._TAG_TEMP_WITH,   _queryBuilderWiths?.toSql()    ?: "");
        temp =  temp?.replace(QueryTools_select._TAG_TEMP_SELECT,          _queryBuilderSelect?.toSql()   ?: "");
        temp =  temp?.replace(QueryTools_table._TAG_TEMP_TABLES,           _queryBuilderTable?.toSql()    ?: "");
        temp =  temp?.replace(QueryTools_joinsConnect._TAG_TEMP_JOINS,     _queryBuilderJoins?.toSql()    ?: "");
        temp =  temp?.replace(QueryTools_where._TAG_TEMP_WHERES,           _queryBuilderWhere?.toSql()    ?: "");
        temp =  temp?.replace(QueryTools_options._TAG_TEMP_OPTION,         _queryBuilderOptions?.toSql()  ?: "");
        return  temp;
    }





    /* ------------------------------------
    Builder
    ------------------------------------ */

    override fun getBaseTempSql(): String? {
        return "${QueryTools_withsCollection._TAG_TEMP_WITH} " +
                " ${QueryTools_select._TAG_TEMP_SELECT} " +
                "${QueryTools_table._TAG_TEMP_TABLES} " +
                "${QueryTools_joinsConnect._TAG_TEMP_JOINS} " +
                "${QueryTools_where._TAG_TEMP_WHERES}  " +
                "${QueryTools_options._TAG_TEMP_OPTION} ";
    }

    override fun toSql(): String? {
        var queryTemp = getBaseTempSql();
        queryTemp = setTemplatePartQuery(queryTemp);
        queryTemp = StringTools.normalizeSpaces(queryTemp.toString());
        return queryTemp;
    }

    override fun replaceInBaseTemp(query: String): String {
        return query ;
    }

    override fun toSqlReadable(): String {
        return StringTools.formatSql(this.toSql().toString());
    }



    /* ------------------------------------
      Select
     ----------------------------------- */
    override fun withs(blockWiths: (IQueryTools_withsCollection) -> QueryTools_withsCollection): QueryBuilder {
        _queryBuilderWiths = _queryBuilderWiths?.setupWiths(blockWiths)
        return this;
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
     joins
    ----------------------------------- */
    override fun joins(blockJoins: (IQueryTools_joinsConnect) -> QueryTools_joinsConnect): QueryBuilder {
        _queryBuilderJoins = _queryBuilderJoins?.setupJoins(blockJoins);
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryBuilder {
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