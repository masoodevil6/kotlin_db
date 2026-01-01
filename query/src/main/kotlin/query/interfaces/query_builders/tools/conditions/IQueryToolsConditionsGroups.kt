package gog.my_project.query.interfaces.query_builders.tools.conditions

import gog.my_project.enums.SqlLogical


interface IQueryToolsConditionsGroups : IQueryToolsIsConditions {

    fun getGroupLogical(): String?;
    fun getGroupConditions(): MutableList<IQueryToolsIsConditions>;

    fun logical(logical: SqlLogical): IQueryToolsConditionsGroups;
    fun logicalAnd(): IQueryToolsConditionsGroups;
    fun logicalOr(): IQueryToolsConditionsGroups;
    fun logicalOn(): IQueryToolsConditionsGroups;

    fun addGroup(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsConditionsGroups;

    fun addCondition(blockCondition: IQueryToolsConditions.() -> IQueryToolsConditions): IQueryToolsConditionsGroups;


    /*fun whereAnd(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups;
    fun whereAnd(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups;


    fun whereOn(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups;
    fun whereOn(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups;


    fun whereOr(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups;
    fun whereOr(sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups;


    fun whereIn(sideLeft: String? , listValues: List<String>): IQueryToolsConditionsGroups;
    fun whereIn(sideLeft: String? , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups;


    fun whereLike(sideLeft: String? , search: String? , conditionLogical: String = _LOGICAL_AND) : IQueryToolsConditionsGroups;


    fun whereNull(conditionLogical: String ,  sideLeft: String): IQueryToolsConditionsGroups;
    fun whereNull(conditionLogical: String, block: (IQueryBuilder) -> IQueryBuilder, conditionOperation: String): IQueryToolsConditionsGroups;


    fun whereCondition(conditionLogical: String , sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups;
    fun whereCondition(conditionLogical: String ,sideLeft: String? , conditionOperation: String , block: (IQueryBuilder) -> IQueryBuilder?) : IQueryToolsConditionsGroups;
*/
}