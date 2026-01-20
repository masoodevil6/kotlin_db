package gog.my_project.data_base.query_builder.query.ast

import gog.my_project.data_base.connection.tools.datas.SqlParameter
import gog.my_project.data_base.query_builder.query.ast.join.QueryToolsJoinsConnect
import gog.my_project.data_base.query_builder.query.ast.options.QueryToolsOptionGroup
import gog.my_project.data_base.query_builder.query.ast.options.QueryToolsOptionLimit
import gog.my_project.data_base.query_builder.query.ast.options.QueryToolsOptionOffset
import gog.my_project.data_base.query_builder.query.ast.options.QueryToolsOptionOrder
import gog.my_project.data_base.query_builder.query.ast.select.QueryToolsSelect
import gog.my_project.data_base.query_builder.query.ast.table.QueryToolsTable
import gog.my_project.data_base.query_builder.query.ast.where.QueryToolsWhere
import gog.my_project.data_base.query_builder.query.ast.with.QueryToolsWithsCollection
import gog.my_project.data_base.query_builder.query.interfaces.IQueryBuilder
import gog.my_project.data_base.query_builder.query.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query_builder.query.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query_builder.query.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query_builder.query.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query_builder.query.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query_builder.query.interfaces.with.IQueryToolsWithsCollection


class QueryBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
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
    /*override fun toSql(sqlDialect: ISqlDialect): String? {
        return sqlDialect.getBasicSql(this);
    }*/




    /* ==============================================================
    structure
    ============================================================== */

    /* ------------------------------------
      Select
     ----------------------------------- */
    override fun withs(blockWiths: IQueryToolsWithsCollection.() -> IQueryToolsWithsCollection): IQueryBuilder {
        val builder =
            QueryToolsWithsCollection(
                params
            );
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
    override fun where(blockGroup: IQueryToolsWhere.() -> IQueryToolsWhere): IQueryBuilder {
        val builder = QueryToolsWhere(params);
        _queryBuilderWhere = builder.blockGroup();
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
        return this;
    }


    /* ------------------------------------
     Group
    ----------------------------------- */
    override fun group(blockGroup: IQueryToolsOptionGroup.() -> IQueryToolsOptionGroup): IQueryBuilder {
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