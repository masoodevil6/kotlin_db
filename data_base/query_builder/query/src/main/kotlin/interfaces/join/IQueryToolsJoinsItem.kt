package gog.my_project.data_base.query_builder.query.interfaces.join

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditionsGroups
import gog.my_project.data_base.query_builder.query.interfaces.table.IQueryToolsTable
import gog.my_project.data_base.query_builder.query.tools.enums.SqlTypeJoin


interface IQueryToolsJoinsItem : IQueryTools {

    fun getJoinType(): SqlTypeJoin;
    fun getJoinTable(): IQueryToolsTable;
    fun getJoinConditions(): IQueryToolsConditionsGroups;


    fun innerJoin(): IQueryToolsJoinsItem;
    fun leftJoin(): IQueryToolsJoinsItem;
    fun rightJoin(): IQueryToolsJoinsItem;

    fun table(blockTable: IQueryToolsTable.() -> IQueryToolsTable): IQueryToolsJoinsItem;

    fun condition(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsJoinsItem;
}