package gog.kotlin_db.data.base.QueryBuilder


import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroup
import gog.kotlin_db.data.base.QueryBuilder.tools.options.IQueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.options.QueryTools_options
import gog.kotlin_db.data.base.QueryBuilder.tools.select.IQueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.select.QueryTools_select
import gog.kotlin_db.data.base.QueryBuilder.tools.table.IQueryTools_table
import gog.kotlin_db.data.base.QueryBuilder.tools.table.QueryTools_table

interface IQueryBuilder: IQueryTools {


    fun select(blockSelect: (IQueryTools_select) -> QueryTools_select): QueryBuilder;
    fun table(blockTable: (IQueryTools_table) -> QueryTools_table): QueryBuilder;
    fun where(blockGroup: (QueryTools_conditionsGroup) -> QueryTools_conditionsGroup): QueryBuilder;
    fun options(blockGroup: (IQueryTools_options) -> QueryTools_options): QueryBuilder;



}