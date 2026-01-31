package gog.my_project.data_base.query.builder.ast

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.builder.ast.join.QueryToolsJoinsConnect
import gog.my_project.data_base.query.builder.ast.options.QueryToolsOptionGroup
import gog.my_project.data_base.query.builder.ast.options.QueryToolsOptionLimit
import gog.my_project.data_base.query.builder.ast.options.QueryToolsOptionOffset
import gog.my_project.data_base.query.builder.ast.options.QueryToolsOptionOrder
import gog.my_project.data_base.query.builder.ast.select.QueryToolsSelect
import gog.my_project.data_base.query.builder.ast.table.QueryToolsTable
import gog.my_project.data_base.query.builder.ast.where.QueryToolsWhere
import gog.my_project.data_base.query.builder.ast.with.QueryToolsWithsCollection
import gog.my_project.data_base.query.builder.interfaces.IQueryBuilder
import gog.my_project.data_base.query.builder.interfaces.join.IQueryToolsJoinsConnect
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionGroup
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionLimit
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOffset
import gog.my_project.data_base.query.builder.interfaces.options.IQueryToolsOptionOrder
import gog.my_project.data_base.query.builder.interfaces.select.IQueryToolsSelect
import gog.my_project.data_base.query.builder.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query.builder.interfaces.where.IQueryToolsWhere
import gog.my_project.data_base.query.builder.interfaces.with.IQueryToolsWithsCollection
import gog.my_project.data_base.query.definition.interfaces.IQueryDefinition
import gog.my_project.data_base.query.definition.interfaces.join.IQueryDefinitionJoinsConnect
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionGroup
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionLimit
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionOffset
import gog.my_project.data_base.query.definition.interfaces.options.IQueryDefinitionOptionOrder
import gog.my_project.data_base.query.definition.interfaces.select.IQueryDefinitionSelect
import gog.my_project.data_base.query.definition.interfaces.table.IQueryDefinitionTable
import gog.my_project.data_base.query.definition.interfaces.where.IQueryDefinitionWhere
import gog.my_project.data_base.query.definition.interfaces.with.IQueryDefinitionWithsCollection

class QueryBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) :
    IQueryBuilder , IQueryDefinition
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
    structure
    ============================================================== */

    /* ------------------------------------
      Select
     ----------------------------------- */
    override fun withs(blockWiths: IQueryDefinitionWithsCollection.() -> IQueryDefinitionWithsCollection): IQueryDefinition {
        val builder =
            QueryToolsWithsCollection(
                params
            );
        _queryBuilderWiths = builder.blockWiths() as IQueryToolsWithsCollection?;
        return this;
    }


    /* ------------------------------------
       Select
     ----------------------------------- */
    override fun select(blockSelect: IQueryDefinitionSelect.() -> IQueryDefinitionSelect): IQueryDefinition {
        val builder = QueryToolsSelect(params);
        _queryBuilderSelect = builder.blockSelect() as IQueryToolsSelect?;
        return this;
    }




    /* ------------------------------------
       From
    ----------------------------------- */
    override fun table(blockTable: IQueryDefinitionTable.() -> IQueryDefinitionTable): IQueryDefinition {
        val builder = QueryToolsTable(params);
        _queryBuilderTable = builder.blockTable() as IQueryToolsTable?;
        return this;
    }




    /* ------------------------------------
     joins
    ----------------------------------- */
    override fun joins(blockJoins: IQueryDefinitionJoinsConnect.() -> IQueryDefinitionJoinsConnect): IQueryDefinition {
        val builder = QueryToolsJoinsConnect(params);
        _queryBuilderJoins = builder.blockJoins() as IQueryToolsJoinsConnect?;
        return this;
    }




    /* ------------------------------------
      where
    ----------------------------------- */
    override fun where(blockGroup: IQueryDefinitionWhere.() -> IQueryDefinitionWhere): IQueryDefinition {
        val builder = QueryToolsWhere(params);
        _queryBuilderWhere = builder.blockGroup() as IQueryToolsWhere?;
        return this;
    }







    /* ------------------------------------
      Limit / Offset
    ----------------------------------- */
    override fun limit(blockLimit: IQueryDefinitionOptionLimit.() -> IQueryDefinitionOptionLimit) : IQueryDefinition {
        val builder = QueryToolsOptionLimit(params);
        _queryBuilderOptionLimit = builder.blockLimit() as IQueryToolsOptionLimit?;
        return this;
    }

    override fun offset(blockOffset: IQueryDefinitionOptionOffset.() -> IQueryDefinitionOptionOffset): IQueryDefinition {
        val builder = QueryToolsOptionOffset(params);
        _queryBuilderOptionOffset = builder.blockOffset() as IQueryToolsOptionOffset?;
        return this;
    }


    /* ------------------------------------
     Group
    ----------------------------------- */
    override fun group(blockGroup: IQueryDefinitionOptionGroup.() -> IQueryDefinitionOptionGroup): IQueryDefinition {
        val builder = QueryToolsOptionGroup(params);
        _queryBuilderOptionGroup = builder.blockGroup() as IQueryToolsOptionGroup?;

        return this;
    }


    /* ------------------------------------
     order
    ----------------------------------- */
    override fun order(blockOrder: IQueryDefinitionOptionOrder.() -> IQueryDefinitionOptionOrder): IQueryDefinition {
        val builder = QueryToolsOptionOrder(params);
        _queryBuilderOptionOrder = builder.blockOrder() as IQueryToolsOptionOrder?;
        return this;
    }


}