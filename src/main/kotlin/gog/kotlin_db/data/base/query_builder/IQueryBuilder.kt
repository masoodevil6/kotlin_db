package gog.kotlin_db.data.base.query_builder


import gog.kotlin_db.data.base.query_builder.tools.IQueryTools
import gog.kotlin_db.data.base.query_builder.tools.option_group.IQueryToolsOptionGroup
import gog.kotlin_db.data.base.query_builder.tools.option_group.QueryToolsOptionGroup
import gog.kotlin_db.data.base.query_builder.tools.option_order.IQueryToolsOptionOrder
import gog.kotlin_db.data.base.query_builder.tools.option_order.QueryToolsOptionOrder
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups
import gog.kotlin_db.data.base.query_builder.tools.join.IQueryToolsJoinsConnect
import gog.kotlin_db.data.base.query_builder.tools.join.QueryToolsJoinsConnect
import gog.kotlin_db.data.base.query_builder.tools.select.IQueryToolsSelect
import gog.kotlin_db.data.base.query_builder.tools.select.QueryToolsSelect
import gog.kotlin_db.data.base.query_builder.tools.table.IQueryToolsTable
import gog.kotlin_db.data.base.query_builder.tools.table.QueryToolsTable
import gog.kotlin_db.data.base.query_builder.tools.with.IQueryToolsWithsCollection
import gog.kotlin_db.data.base.query_builder.tools.with.QueryToolsWithsCollection

interface IQueryBuilder: IQueryTools {


    fun withs(blockWiths: (IQueryToolsWithsCollection) -> QueryToolsWithsCollection): QueryBuilder;
    fun select(blockSelect: (IQueryToolsSelect) -> QueryToolsSelect): QueryBuilder;
    fun table(blockTable: (IQueryToolsTable) -> QueryToolsTable): QueryBuilder;
    fun joins(blockJoins: (IQueryToolsJoinsConnect) -> QueryToolsJoinsConnect): QueryBuilder;
    fun where(blockGroup: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryBuilder;

    fun pageInit(optionLimit: Int , optionOffset: Int) : QueryBuilder;
    fun limit(optionLimit: Int) : QueryBuilder;
    fun offset(optionOffset: Int) : QueryBuilder;

    fun group(blockGroup: (IQueryToolsOptionGroup) -> QueryToolsOptionGroup): QueryBuilder;

    fun order(blockOrder: (IQueryToolsOptionOrder) -> QueryToolsOptionOrder): QueryBuilder;

    fun toSqlReadable(): String;


}