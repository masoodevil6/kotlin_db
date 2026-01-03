package gog.my_project.query.interfaces.query_builders

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
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


interface IQueryBuilder: IQueryTools {

    var params: MutableList<Any?>

    fun getQueryWiths(): IQueryToolsWithsCollection?;
    fun getQuerySelect(): IQueryToolsSelect?;
    fun getQueryTable(): IQueryToolsTable?;
    fun getQueryJoins(): IQueryToolsJoinsConnect?;
    fun getQueryWhere(): IQueryToolsWhere?;
    fun getQueryOptionLimit(): IQueryToolsOptionLimit?;
    fun getQueryOptionOffset(): IQueryToolsOptionOffset?;
    fun getQueryOptionGroup(): IQueryToolsOptionGroup?;
    fun getQueryOptionOrder(): IQueryToolsOptionOrder?;

    fun withs(blockWiths: IQueryToolsWithsCollection.() -> IQueryToolsWithsCollection): IQueryBuilder;
    fun select(blockSelect: IQueryToolsSelect.() -> IQueryToolsSelect): IQueryBuilder;
    fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryBuilder;
    fun joins(blockJoins: IQueryToolsJoinsConnect.() -> IQueryToolsJoinsConnect): IQueryBuilder;
    fun where(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryBuilder;

//    fun pageInit(optionLimit: Long , optionOffset: Long) : IQueryBuilder;
//    fun limit(optionLimit: Long) : IQueryBuilder;
//    fun offset(optionOffset: Long) : IQueryBuilder;
//
    fun limit(blockLimit: IQueryToolsOptionLimit.() -> IQueryToolsOptionLimit) : IQueryBuilder ;
    fun offset(blockOffset: IQueryToolsOptionOffset.() -> IQueryToolsOptionOffset) : IQueryBuilder ;

    fun group(blockGroup: IQueryToolsOptionGroup.() -> IQueryToolsOptionGroup): IQueryBuilder;

    fun order(blockOrder: IQueryToolsOptionOrder.() -> IQueryToolsOptionOrder): IQueryBuilder;



}