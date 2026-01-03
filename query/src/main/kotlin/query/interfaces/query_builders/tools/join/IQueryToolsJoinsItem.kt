package gog.my_project.query.interfaces.query_builders.tools.join

import gog.my_project.enums.SqlTypeJoin
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable

interface IQueryToolsJoinsItem : IQueryTools {

    var params: MutableList<Any?>

    fun getJoinType(): SqlTypeJoin;
    fun getJoinTable(): IQueryToolsTable;
    fun getJoinConditions(): IQueryToolsConditionsGroups;


    fun innerJoin(): IQueryToolsJoinsItem;
    fun leftJoin(): IQueryToolsJoinsItem;
    fun rightJoin(): IQueryToolsJoinsItem;

    fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryToolsJoinsItem;

    fun condition(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsJoinsItem;
}