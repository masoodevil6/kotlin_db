package gog.kotlin_db.data.base.query_builder

import gog.kotlin_db.Configs.DatabaseConfig
import gog.kotlin_db.data.base.query_builder.tools.option_group.IQueryToolsOptionGroup
import gog.kotlin_db.data.base.query_builder.tools.option_group.QueryToolsOptionGroup
import gog.kotlin_db.data.base.query_builder.tools.option_limit.IQueryToolsOptionLimit
import gog.kotlin_db.data.base.query_builder.tools.option_limit.QueryToolsOptionLimit
import gog.kotlin_db.data.base.query_builder.tools.option_offset.IQueryToolsOptionOffset
import gog.kotlin_db.data.base.query_builder.tools.option_offset.QueryToolsOptionOffset
import gog.kotlin_db.data.base.query_builder.tools.option_order.IQueryToolsOptionOrder
import gog.kotlin_db.data.base.query_builder.tools.option_order.QueryToolsOptionOrder
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups
import gog.kotlin_db.data.base.query_builder.tools.join.IQueryToolsJoinsConnect
import gog.kotlin_db.data.base.query_builder.tools.join.QueryToolsJoinsConnect
import gog.kotlin_db.data.base.query_builder.tools.select.IQueryToolsSelect
import gog.kotlin_db.data.base.query_builder.tools.select.QueryToolsSelect
import gog.kotlin_db.data.base.query_builder.tools.table.IQueryToolsTable
import gog.kotlin_db.data.base.query_builder.tools.table.QueryToolsTable
import gog.kotlin_db.data.base.query_builder.tools.where.IQueryToolsWhere
import gog.kotlin_db.data.base.query_builder.tools.where.QueryToolsWhere
import gog.kotlin_db.data.base.query_builder.tools.with.IQueryToolsWithsCollection
import gog.kotlin_db.data.base.query_builder.tools.with.QueryToolsWithsCollection
import gog.kotlin_db.utils.params.ObjectSqlTypeTemplates
import gog.kotlin_db.utils.tools.StringTools


class QueryBuilder(

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
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_WITH,             _queryBuilderWiths?.toSql()            ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_SELECT,           _queryBuilderSelect?.toSql()           ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_TABLES,           _queryBuilderTable?.toSql()            ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_JOINS,            _queryBuilderJoins?.toSql()            ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_WHERES,           _queryBuilderWhere?.toSql()            ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_LIMIT,     _queryBuilderOptionLimit?.toSql()      ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_OFFSET,    _queryBuilderOptionOffset?.toSql()     ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_GROUP,     _queryBuilderOptionGroup?.toSql()      ?: "");
        temp =  temp?.replace(ObjectSqlTypeTemplates._TAG_TEMP_OPTION_ORDER,     _queryBuilderOptionOrder?.toSql()      ?: "");
        return  temp;
    }





    /* ------------------------------------
    Builder
    ------------------------------------ */

    override fun getBaseTempSql(): String? {
        return DatabaseConfig.dbTypeName.getBasicSql();
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
    override fun withs(blockWiths: (IQueryToolsWithsCollection) -> QueryToolsWithsCollection): QueryBuilder {
        _queryBuilderWiths = _queryBuilderWiths?.setupWiths(blockWiths)
        return this;
    }


    /* ------------------------------------
       Select
     ----------------------------------- */
    override fun select(blockSelect: (IQueryToolsSelect) -> QueryToolsSelect): QueryBuilder {
        _queryBuilderSelect = _queryBuilderSelect?.selectSetup(blockSelect)
        return this;
    }




    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(blockTable: (IQueryToolsTable) -> QueryToolsTable): QueryBuilder {
        _queryBuilderTable = _queryBuilderTable?.tableSetup(blockTable)
        return this;
    }




    /* ------------------------------------
     joins
    ----------------------------------- */
    override fun joins(blockJoins: (IQueryToolsJoinsConnect) -> QueryToolsJoinsConnect): QueryBuilder {
        _queryBuilderJoins = _queryBuilderJoins?.setupJoins(blockJoins);
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryBuilder {
        _queryBuilderWhere = _queryBuilderWhere?.whereSetup(blockGroup)
        return this;
    }









    /* ------------------------------------
      Limit / Offset
    ----------------------------------- */

    override fun pageInit(
        optionLimit: Int,
        optionOffset: Int
    ): QueryBuilder {
        _queryBuilderOptionLimit = _queryBuilderOptionLimit?.setOptionLimit(optionLimit);
        _queryBuilderOptionOffset = _queryBuilderOptionOffset?.setOptionOffset(optionOffset);
        return this;
    }

    override fun limit(optionLimit: Int): QueryBuilder {
        _queryBuilderOptionLimit = _queryBuilderOptionLimit?.setOptionLimit(optionLimit);
        return this;
    }

    override fun offset(optionOffset: Int): QueryBuilder {
        _queryBuilderOptionOffset = _queryBuilderOptionOffset?.setOptionOffset(optionOffset);
        return this;
    }


    /* ------------------------------------
     Group
    ----------------------------------- */
    override fun group(blockGroup: (IQueryToolsOptionGroup) -> QueryToolsOptionGroup): QueryBuilder {
        _queryBuilderOptionGroup = _queryBuilderOptionGroup?.groupSetup(blockGroup);
        return this;
    }


    /* ------------------------------------
     order
    ----------------------------------- */
    override fun order(blockOrder: (IQueryToolsOptionOrder) -> QueryToolsOptionOrder): QueryBuilder {
        _queryBuilderOptionOrder = _queryBuilderOptionOrder?.orderSetup(blockOrder);
        return this;
    }


}