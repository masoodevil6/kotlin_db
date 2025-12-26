package gog.my_project.query.query_builder

import gog.my_project.data_base.config.DialectQuery
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
import gog.my_project.query.interfaces.query_builders.tools.with.IQueryToolsWithsCollection
import gog.my_project.query.interfaces.sql_dialect.ISqlDialect
import gog.my_project.query.query_builder.tools.join.QueryToolsJoinsConnect
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionGroup
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionLimit
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionOffset
import gog.my_project.query.query_builder.tools.options.QueryToolsOptionOrder
import gog.my_project.query.query_builder.tools.select.QueryToolsSelect
import gog.my_project.query.query_builder.tools.table.QueryToolsTable
import gog.my_project.query.query_builder.tools.where.QueryToolsWhere
import gog.my_project.query.query_builder.tools.with.QueryToolsWithsCollection
import gog.my_project.tools.scripts.tools.scripts.StringTools
import gog.my_project.tools.templates.OTemplateSqlDialect


class QueryBuilder(
    private val sqlDialect: ISqlDialect
) :
    IQueryBuilder
{




    var _queryBuilderWiths : IQueryToolsWithsCollection? = QueryToolsWithsCollection();
    var _queryBuilderSelect : IQueryToolsSelect? = QueryToolsSelect();
    var _queryBuilderTable : IQueryToolsTable? = QueryToolsTable();
    var _queryBuilderJoins : IQueryToolsJoinsConnect? = QueryToolsJoinsConnect();
    var _queryBuilderWhere : IQueryToolsWhere? = QueryToolsWhere();
    var _queryBuilderOptionLimit : IQueryToolsOptionLimit? = QueryToolsOptionLimit();
    var _queryBuilderOptionOffset : IQueryToolsOptionOffset? = QueryToolsOptionOffset();
    var _queryBuilderOptionGroup : IQueryToolsOptionGroup? = QueryToolsOptionGroup();
    var _queryBuilderOptionOrder : IQueryToolsOptionOrder? = QueryToolsOptionOrder();



    private fun setTemplatePartQuery(queryTemp: String?): String? {
        var temp = queryTemp;
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_WITH,             _queryBuilderWiths?.toSql()            ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_SELECT,           _queryBuilderSelect?.toSql()           ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_TABLES,           _queryBuilderTable?.toSql()            ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_JOINS,            _queryBuilderJoins?.toSql()            ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_WHERES,           _queryBuilderWhere?.toSql()            ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_LIMIT,     _queryBuilderOptionLimit?.toSql()      ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_OFFSET,    _queryBuilderOptionOffset?.toSql()     ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_GROUP,     _queryBuilderOptionGroup?.toSql()      ?: "");
        temp =  temp?.replace(OTemplateSqlDialect._TAG_TEMP_OPTION_ORDER,     _queryBuilderOptionOrder?.toSql()      ?: "");
        return  temp;
    }





    /* ------------------------------------
    Builder
    ------------------------------------ */

    override fun getBaseTempSql(): String? {
        return sqlDialect.getBasicSql();
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
    override fun withs(blockWiths: (IQueryToolsWithsCollection) -> IQueryToolsWithsCollection): IQueryBuilder {
        _queryBuilderWiths = _queryBuilderWiths?.setupWiths(blockWiths)
        return this;
    }


    /* ------------------------------------
       Select
     ----------------------------------- */
    override fun select(blockSelect: (IQueryToolsSelect) -> IQueryToolsSelect): IQueryBuilder {
        _queryBuilderSelect = _queryBuilderSelect?.selectSetup(blockSelect)
        return this;
    }




    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(blockTable: (IQueryToolsTable) -> IQueryToolsTable): IQueryBuilder {
        _queryBuilderTable = _queryBuilderTable?.tableSetup(blockTable)
        return this;
    }




    /* ------------------------------------
     joins
    ----------------------------------- */
    override fun joins(blockJoins: (IQueryToolsJoinsConnect) -> IQueryToolsJoinsConnect): IQueryBuilder {
        _queryBuilderJoins = _queryBuilderJoins?.setupJoins(blockJoins);
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryBuilder {
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
    override fun group(blockGroup: (IQueryToolsOptionGroup) -> IQueryToolsOptionGroup): IQueryBuilder{
        _queryBuilderOptionGroup = _queryBuilderOptionGroup?.groupSetup(blockGroup);
        return this;
    }


    /* ------------------------------------
     order
    ----------------------------------- */
    override fun order(blockOrder: (IQueryToolsOptionOrder) -> IQueryToolsOptionOrder): IQueryBuilder {
        _queryBuilderOptionOrder = _queryBuilderOptionOrder?.orderSetup(blockOrder);
        return this;
    }


}