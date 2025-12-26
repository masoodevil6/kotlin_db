package gog.my_project.query.interfaces.query_builders.tools.conditions

import gog.my_project.query.interfaces.query_builders.IQueryBuilder
import gog.my_project.query.query_builder.tools.conditions.QueryToolsConditionsGroups.Companion._LOGICAL_AND


interface IQueryToolsConditionsGroups : IQueryToolsIsConditions {


    fun group( conditionLogical: String , block: (IQueryToolsConditionsGroups) -> IQueryToolsIsConditions): IQueryToolsConditionsGroups;


    fun whereAnd(sideLeft: String? , conditionOperation: String , sideRight: String?): IQueryToolsConditionsGroups;
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

}