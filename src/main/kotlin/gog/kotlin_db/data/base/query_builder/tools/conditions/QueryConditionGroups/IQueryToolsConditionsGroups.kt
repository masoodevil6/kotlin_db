package gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups

import gog.kotlin_db.data.base.query_builder.tools.conditions.IQueryToolsIsConditions
import gog.kotlin_db.data.base.query_builder.QueryBuilder

interface IQueryToolsConditionsGroups : IQueryToolsIsConditions {


    fun group( conditionLogical: String , block: (QueryToolsConditionsGroups) -> IQueryToolsIsConditions): QueryToolsConditionsGroups;


    fun whereAnd(sideLeft: String? , conditionOperation: String , sideRight: String?): QueryToolsConditionsGroups ;
    fun whereAnd(sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryToolsConditionsGroups ;


    fun whereOn(sideLeft: String? , conditionOperation: String , sideRight: String?): QueryToolsConditionsGroups ;
    fun whereOn(sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryToolsConditionsGroups ;


    fun whereOr(sideLeft: String? , conditionOperation: String , sideRight: String?): QueryToolsConditionsGroups ;
    fun whereOr(sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryToolsConditionsGroups ;


    fun whereIn(sideLeft: String? , listValues: List<String>): QueryToolsConditionsGroups ;
    fun whereIn(sideLeft: String? , block: (QueryBuilder) -> QueryBuilder?) : QueryToolsConditionsGroups;


    fun whereLike(sideLeft: String? , search: String? , conditionLogical: String = QueryToolsConditionsGroups._LOGICAL_AND) : QueryToolsConditionsGroups;


    fun whereNull(conditionLogical: String ,  sideLeft: String): QueryToolsConditionsGroups ;
    fun whereNull(conditionLogical: String ,  block: (QueryBuilder) -> QueryBuilder , conditionOperation: String): QueryToolsConditionsGroups ;


    fun whereCondition(conditionLogical: String , sideLeft: String? , conditionOperation: String , sideRight: String?): QueryToolsConditionsGroups;
    fun whereCondition(conditionLogical: String ,sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryToolsConditionsGroups;

}