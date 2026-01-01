package gog.my_project.query.interfaces.query_builders.tools.join

import gog.my_project.enums.SqlTypeJoin
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups
import gog.my_project.query.interfaces.query_builders.tools.table.IQueryToolsTable

interface IQueryToolsJoinsItem : IQueryTools {

    fun getJoinType(): String;
    fun getJoinTable(): IQueryToolsTable;
    fun getJoinConditions(): IQueryToolsConditionsGroups;

    fun typeJoin(joinType: SqlTypeJoin): IQueryToolsJoinsItem;
    fun innerJoin(): IQueryToolsJoinsItem;
    fun leftJoin(): IQueryToolsJoinsItem;
    fun rightJoin(): IQueryToolsJoinsItem;

    fun tableJoin(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryToolsJoinsItem;

    fun conditionJoin(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsJoinsItem;
}