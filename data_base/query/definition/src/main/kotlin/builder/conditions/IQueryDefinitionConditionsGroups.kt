package gog.my_project.data_base.query.definition.interfaces.conditions

import gog.my_project.data_base.query.definition.tools.enums.SqlLogical

interface IQueryDefinitionConditionsGroups {

    fun logical(logical: SqlLogical): IQueryDefinitionConditionsGroups;
    fun logicalAnd(): IQueryDefinitionConditionsGroups;
    fun logicalOr(): IQueryDefinitionConditionsGroups;
    fun logicalOn(): IQueryDefinitionConditionsGroups;

    fun addGroup(blockGroup: IQueryDefinitionConditionsGroups.() -> IQueryDefinitionConditionsGroups): IQueryDefinitionConditionsGroups;

    fun addCondition(blockCondition: IQueryDefinitionConditions.() -> IQueryDefinitionConditions): IQueryDefinitionConditionsGroups;


}