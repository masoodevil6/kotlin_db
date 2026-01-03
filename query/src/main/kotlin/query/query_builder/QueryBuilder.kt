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
import gog.my_project.query.query_builder.tools.with.collections.QueryToolsWithsCollection
import gog.my_project.tools.scripts.StringTools


class QueryBuilder(
    override var params: MutableList<Any?> = mutableListOf<Any?>()
) :
    IQueryBuilder
{

    var _queryBuilderWiths : IQueryToolsWithsCollection? = null;
    var _queryBuilderSelect : IQueryToolsSelect? = null;
    var _queryBuilderTable : IQueryToolsTable? = null;
    var _queryBuilderJoins : IQueryToolsJoinsConnect? = null;
    var _queryBuilderWhere : IQueryToolsWhere? = null;
    var _queryBuilderOptionLimit : IQueryToolsOptionLimit? = null;
    var _queryBuilderOptionOffset : IQueryToolsOptionOffset? = null;
    var _queryBuilderOptionGroup : IQueryToolsOptionGroup? = null;
    var _queryBuilderOptionOrder : IQueryToolsOptionOrder? = null;



    /* ==============================================================
    template
    ============================================================== */
    override fun getQueryWiths(): IQueryToolsWithsCollection? {
        return _queryBuilderWiths;
    }

    override fun getQuerySelect(): IQueryToolsSelect? {
        return _queryBuilderSelect;
    }

    override fun getQueryTable(): IQueryToolsTable? {
        return _queryBuilderTable;
    }

    override fun getQueryJoins(): IQueryToolsJoinsConnect? {
        return _queryBuilderJoins;
    }

    override fun getQueryWhere(): IQueryToolsWhere? {
        return _queryBuilderWhere;
    }

    override fun getQueryOptionLimit(): IQueryToolsOptionLimit? {
        return _queryBuilderOptionLimit;
    }

    override fun getQueryOptionOffset(): IQueryToolsOptionOffset? {
        return _queryBuilderOptionOffset;
    }

    override fun getQueryOptionGroup(): IQueryToolsOptionGroup? {
        return _queryBuilderOptionGroup;
    }

    override fun getQueryOptionOrder(): IQueryToolsOptionOrder? {
        return _queryBuilderOptionOrder;
    }





    /* ==============================================================
    Builder
    ============================================================== */
    override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getBasicSql(this);
    }







    /* ==============================================================
    structure
    ============================================================== */

    /* ------------------------------------
      Select
     ----------------------------------- */
    override fun withs(blockWiths: IQueryToolsWithsCollection.() -> IQueryToolsWithsCollection): IQueryBuilder {
        val builder = QueryToolsWithsCollection(params);
        _queryBuilderWiths =builder.blockWiths();
        return this;
    }


    /* ------------------------------------
       Select
     ----------------------------------- */
    override fun select(blockSelect: IQueryToolsSelect.() -> IQueryToolsSelect): IQueryBuilder {
        val builder = QueryToolsSelect(params);
        _queryBuilderSelect = builder.blockSelect();
        return this;
    }




    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryBuilder {
        val builder = QueryToolsTable(params);
        _queryBuilderTable = builder.blockTable();
        return this;
    }




    /* ------------------------------------
     joins
    ----------------------------------- */
    override fun joins(blockJoins: IQueryToolsJoinsConnect.() -> IQueryToolsJoinsConnect): IQueryBuilder {
        val builder = QueryToolsJoinsConnect(params);
        _queryBuilderJoins = builder.blockJoins();
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryBuilder {
        /*val builder = QueryToolsWhere();
        _queryBuilderWhere = builder.blockGroup();
        return this;*/
        _queryBuilderWhere = _queryBuilderWhere?.whereSetup(blockGroup)
        return this;
    }







    /* ------------------------------------
      Limit / Offset
    ----------------------------------- */
    override fun limit(blockLimit: IQueryToolsOptionLimit.() -> IQueryToolsOptionLimit) : IQueryBuilder {
        val builder = QueryToolsOptionLimit(params);
        _queryBuilderOptionLimit = builder.blockLimit();
        return this;
    }

    override fun offset(blockOffset: IQueryToolsOptionOffset.() -> IQueryToolsOptionOffset): IQueryBuilder {
        val builder = QueryToolsOptionOffset(params);
        _queryBuilderOptionOffset = builder.blockOffset();
        params = _queryBuilderOptionOffset?.params!!
        return this;
    }


    /* ------------------------------------
     Group
    ----------------------------------- */
    override fun group(blockGroup: IQueryToolsOptionGroup.() -> IQueryToolsOptionGroup): IQueryBuilder{
        val builder = QueryToolsOptionGroup(params);
        _queryBuilderOptionGroup = builder.blockGroup();

        return this;
    }


    /* ------------------------------------
     order
    ----------------------------------- */
    override fun order(blockOrder: IQueryToolsOptionOrder.() -> IQueryToolsOptionOrder): IQueryBuilder {
        val builder = QueryToolsOptionOrder(params);
        _queryBuilderOptionOrder = builder.blockOrder();
        return this;
    }


}