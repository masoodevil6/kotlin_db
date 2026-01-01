package gog.my_project.query.query_builder

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionGroup
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionLimit
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOffset
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOrder
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.query_builders.tools.where.IQueryToolsWhere
import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.join.QueryToolsJoinsConnect
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionGroup
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionLimit
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionOffset
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionOrder
import gog.my_project.query.query_builder.tools.select.QueryToolsSelect
import gog.my_project.query.query_builder.tools.table.QueryToolsTable
import gog.my_project.query.query_builder.tools.where.QueryToolsWhere
import gog.my_project.query.query_builder.tools.with.collections.QueryToolsWithsCollection
import gog.my_project.tools.scripts.tools.scripts.StringTools
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryBuilder(
    private val sqlDialect: ISqlDialect
) :
    IQueryBuilder
{




    var _queryBuilderWiths : IQueryToolsWithsCollection? = QueryToolsWithsCollection(sqlDialect);
    var _queryBuilderSelect : IQueryToolsSelect? = QueryToolsSelect(sqlDialect);
    var _queryBuilderTable : IQueryToolsTable? = QueryToolsTable(sqlDialect);
    var _queryBuilderJoins : IQueryToolsJoinsConnect? = QueryToolsJoinsConnect(sqlDialect);
    var _queryBuilderWhere : IQueryToolsWhere? = QueryToolsWhere(sqlDialect);
    var _queryBuilderOptionLimit : IQueryToolsOptionLimit? = QueryToolsOptionLimit(sqlDialect);
    var _queryBuilderOptionOffset : IQueryToolsOptionOffset? = QueryToolsOptionOffset(sqlDialect);
    var _queryBuilderOptionGroup : IQueryToolsOptionGroup? = QueryToolsOptionGroup(sqlDialect);
    var _queryBuilderOptionOrder : IQueryToolsOptionOrder? = QueryToolsOptionOrder(sqlDialect);



    private fun setTemplatePartQuery(queryTemp: String): String {
        var temp = queryTemp;
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_WITH,             _queryBuilderWiths?.toSql()            ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_SELECT,           _queryBuilderSelect?.toSql()           ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_TABLES,           _queryBuilderTable?.toSql()            ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_JOINS,            _queryBuilderJoins?.toSql()            ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_WHERES,           _queryBuilderWhere?.toSql()            ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_LIMIT,     _queryBuilderOptionLimit?.toSql()      ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_OFFSET,    _queryBuilderOptionOffset?.toSql()     ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_GROUP,     _queryBuilderOptionGroup?.toSql()      ?: "");
        temp =  temp.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_ORDER,     _queryBuilderOptionOrder?.toSql()      ?: "");
        return  temp;
    }





    /* ------------------------------------
    Builder
    ------------------------------------ */


    override fun toSql(): String? {
        var queryTemp = sqlDialect.getBasicSql();
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
    override fun withs(blockWiths: IQueryToolsWithsCollection.() -> IQueryToolsWithsCollection): IQueryBuilder {
        val builder = QueryToolsWithsCollection(sqlDialect);
        _queryBuilderWiths =builder.blockWiths();
        return this;
    }


    /* ------------------------------------
       Select
     ----------------------------------- */
    override fun select(blockSelect: IQueryToolsSelect.() -> IQueryToolsSelect): IQueryBuilder {
        val builder = QueryToolsSelect(sqlDialect);
        _queryBuilderSelect = builder.blockSelect();
        return this;
    }




    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryBuilder {
        val builder = QueryToolsTable(sqlDialect);
        _queryBuilderTable = builder.blockTable();
        return this;
    }




    /* ------------------------------------
     joins
    ----------------------------------- */
    override fun joins(blockJoins: IQueryToolsJoinsConnect.() -> IQueryToolsJoinsConnect): IQueryBuilder {
        val builder = QueryToolsJoinsConnect(sqlDialect);
        _queryBuilderJoins = builder.blockJoins();
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryBuilder {
     /*   val builder = QueryToolsWhere(sqlDialect);
        _queryBuilderWhere = builder.blockGroup();
        return this;*/

        _queryBuilderWhere = _queryBuilderWhere?.whereSetup(blockGroup)
        return this;
    }









    /* ------------------------------------
      Limit / Offset
    ----------------------------------- */

    override fun pageInit(optionLimit: Int , optionOffset: Int) : IQueryBuilder {
        _queryBuilderOptionLimit = _queryBuilderOptionLimit?.setOptionLimit(optionLimit);
        _queryBuilderOptionOffset = _queryBuilderOptionOffset?.setOptionOffset(optionOffset);
        return this;
    }

    override fun limit(optionLimit: Int) : IQueryBuilder {
        _queryBuilderOptionLimit = _queryBuilderOptionLimit?.setOptionLimit(optionLimit);
        return this;
    }

    override fun offset(optionOffset: Int) : IQueryBuilder {
        _queryBuilderOptionOffset = _queryBuilderOptionOffset?.setOptionOffset(optionOffset);
        return this;
    }


    /* ------------------------------------
     Group
    ----------------------------------- */
    override fun group(blockGroup: IQueryToolsOptionGroup.() -> IQueryToolsOptionGroup): IQueryBuilder{
        val builder = QueryToolsOptionGroup(sqlDialect);
        _queryBuilderOptionGroup = builder.blockGroup();
        return this;
    }


    /* ------------------------------------
     order
    ----------------------------------- */
    override fun order(blockOrder: IQueryToolsOptionOrder.() -> IQueryToolsOptionOrder): IQueryBuilder {
        val builder = QueryToolsOptionOrder(sqlDialect);
        _queryBuilderOptionOrder = builder.blockOrder();
        return this;
    }


}