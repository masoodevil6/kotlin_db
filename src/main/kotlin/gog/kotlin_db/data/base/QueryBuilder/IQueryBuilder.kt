package gog.kotlin_db.data.base.QueryBuilder


import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroups
import gog.kotlin_db.data.base.QueryBuilder.tools.join.IQueryTools_joinsConnect
import gog.kotlin_db.data.base.QueryBuilder.tools.join.QueryTools_joinsConnect
import gog.kotlin_db.data.base.QueryBuilder.tools.options.IQueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.select.IQueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.select.QueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.table.IQueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.table.QueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.with.IQueryTools_withsCollection
import gog.kotlin_db.data.base.QueryBuilder.tools.with.QueryTools_withsCollection

interface IQueryBuilder: IQueryTools {


    fun withs(blockWiths: (IQueryTools_withsCollection) -> QueryTools_withsCollection): QueryBuilder;
    fun select(blockSelect: (IQueryTools_select) -> QueryTools_select): QueryBuilder;
    fun table(blockTable: (IQueryTools_table) -> QueryTools_table): QueryBuilder;
    fun joins(blockJoins: (IQueryTools_joinsConnect) -> QueryTools_joinsConnect): QueryBuilder;
    fun where(blockGroup: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryBuilder;
    fun options(blockGroup: (IQueryTools_options) -> QueryTools_options): QueryBuilder;



}