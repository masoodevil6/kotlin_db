package gog.my_project.query.interfaces.query_builders.tools.conditions

import gog.my_project.datas.SqlParameter
import gog.my_project.enums.SqlLogical


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