package gog.my_project.data_base.query.definition.interfaces.join

import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditionsGroups
import gog.my_project.data_base.query.definition.interfaces.table.IQueryDefinitionTable


interface IQueryDefinitionJoinsItem {

    fun innerJoin(): IQueryDefinitionJoinsItem;
    fun leftJoin(): IQueryDefinitionJoinsItem;
    fun rightJoin(): IQueryDefinitionJoinsItem;

    fun table(blockTable: IQueryDefinitionTable.() -> IQueryDefinitionTable): IQueryDefinitionJoinsItem;

    fun condition(blockCondition: IQueryDefinitionConditionsGroups.() -> IQueryDefinitionConditionsGroups): IQueryDefinitionJoinsItem;

}