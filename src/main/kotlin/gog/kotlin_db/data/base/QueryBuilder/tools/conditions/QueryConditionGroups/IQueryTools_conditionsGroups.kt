package gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.IQueryTools_isConditions
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

interface IQueryTools_conditionsGroups : IQueryTools_isConditions {


    fun group( conditionLogical: String , block: (QueryTools_conditionsGroups) -> IQueryTools_isConditions): QueryTools_conditionsGroups;


    fun whereAnd(sideLeft: String? , conditionOperation: String , sideRight: String?): QueryTools_conditionsGroups ;
    fun whereAnd(sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryTools_conditionsGroups ;


    fun whereOn(sideLeft: String? , conditionOperation: String , sideRight: String?): QueryTools_conditionsGroups ;
    fun whereOn(sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryTools_conditionsGroups ;


    fun whereOr(sideLeft: String? , conditionOperation: String , sideRight: String?): QueryTools_conditionsGroups ;
    fun whereOr(sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryTools_conditionsGroups ;


    fun whereIn(sideLeft: String? , listValues: List<String>): QueryTools_conditionsGroups ;
    fun whereIn(sideLeft: String? , block: (QueryBuilder) -> QueryBuilder?) : QueryTools_conditionsGroups;


    fun whereLike(sideLeft: String? , search: String? , conditionLogical: String = QueryTools_conditionsGroups._LOGICAL_AND) : QueryTools_conditionsGroups;


    fun whereNull(conditionLogical: String ,  sideLeft: String): QueryTools_conditionsGroups ;
    fun whereNull(conditionLogical: String ,  block: (QueryBuilder) -> QueryBuilder , conditionOperation: String): QueryTools_conditionsGroups ;


    fun whereCondition(conditionLogical: String , sideLeft: String? , conditionOperation: String , sideRight: String?): QueryTools_conditionsGroups;
    fun whereCondition(conditionLogical: String ,sideLeft: String? , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder?) : QueryTools_conditionsGroups;

}