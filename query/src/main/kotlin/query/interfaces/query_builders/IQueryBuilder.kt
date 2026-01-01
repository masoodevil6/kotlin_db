package gog.my_project.query.interfaces.query_builders

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.join.IQueryToolsJoinsConnect
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionGroup
import gog.my_project.query.interfaces.query_builders.tools.options.IQueryToolsOptionOrder
import gog.my_project.query.interfaces.query_builders.tools.select.IQueryToolsSelect
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable
import gog.my_project.query.interfaces.query_builders.tools.with.collections.IQueryToolsWithsCollection


interface IQueryBuilder: IQueryTools {

    fun withs(blockWiths: IQueryToolsWithsCollection.() -> IQueryToolsWithsCollection): IQueryBuilder;
    fun select(blockSelect: IQueryToolsSelect.() -> IQueryToolsSelect): IQueryBuilder;
    fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryBuilder;
    fun joins(blockJoins: IQueryToolsJoinsConnect.() -> IQueryToolsJoinsConnect): IQueryBuilder;
    fun where(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryBuilder;

    fun pageInit(optionLimit: Int , optionOffset: Int) : IQueryBuilder;
    fun limit(optionLimit: Int) : IQueryBuilder;
    fun offset(optionOffset: Int) : IQueryBuilder;

    fun group(blockGroup: IQueryToolsOptionGroup.() -> IQueryToolsOptionGroup): IQueryBuilder;

    fun order(blockOrder: IQueryToolsOptionOrder.() -> IQueryToolsOptionOrder): IQueryBuilder;

    fun toSqlReadable(): String;


}