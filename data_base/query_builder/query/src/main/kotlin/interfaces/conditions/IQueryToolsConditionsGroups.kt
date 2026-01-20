package gog.my_project.data_base.query_builder.query.interfaces.conditions

import gog.my_project.data_base.query_builder.query.tools.enums.SqlLogical

interface IQueryToolsConditionsGroups : IQueryToolsIsConditions {

    fun getGroupLogical(): SqlLogical?;
    fun getGroupConditions(): MutableList<IQueryToolsIsConditions>;

    fun logical(logical: SqlLogical): IQueryToolsConditionsGroups;
    fun logicalAnd(): IQueryToolsConditionsGroups;
    fun logicalOr(): IQueryToolsConditionsGroups;
    fun logicalOn(): IQueryToolsConditionsGroups;

    fun addGroup(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsConditionsGroups;

    fun addCondition(blockCondition: IQueryToolsConditions.() -> IQueryToolsConditions): IQueryToolsConditionsGroups;

}