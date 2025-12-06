package gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups

import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.IQueryTools_conditions
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

interface IQueryTools_conditionsGroupMethods {


    fun group( conditionLogical: String , block: (QueryTools_conditionsGroup) -> IQueryTools_conditions): QueryTools_conditionsGroup;


    fun whereAnd(sideLeft: String , conditionOperation: String , sideRight: String): QueryTools_conditionsGroup ;
    fun whereAnd(sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_conditionsGroup ;


    fun whereOn(sideLeft: String , conditionOperation: String , sideRight: String): QueryTools_conditionsGroup ;
    fun whereOn(sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_conditionsGroup ;


    fun whereOr(sideLeft: String , conditionOperation: String , sideRight: String): QueryTools_conditionsGroup ;
    fun whereOr(sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_conditionsGroup ;


    fun whereIn(sideLeft: String , listValues: List<String>): QueryTools_conditionsGroup ;
    fun whereIn(sideLeft: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_conditionsGroup;


    fun whereLike(sideLeft: String , search: String , conditionLogical: String = QueryBuilder._LOGICAL_AND) : QueryTools_conditionsGroup;


    fun whereCondition(conditionLogical: String , sideLeft: String , conditionOperation: String , sideRight: String): QueryTools_conditionsGroup;
    fun whereCondition(conditionLogical: String ,sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryTools_conditionsGroup;

}