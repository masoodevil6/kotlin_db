package gog.kotlin_db.data.base.QueryBuilder.Conditions.QueryConditionGroups

import gog.kotlin_db.data.base.QueryBuilder.Conditions.IQueryBuilder_conditions
import gog.kotlin_db.data.base.QueryBuilder.QueryBuilder

interface IQueryBuilder_conditionsGroupMethods {


    fun group( conditionLogical: String , block: (QueryBuilder_conditionsGroup) -> IQueryBuilder_conditions): QueryBuilder_conditionsGroup;


    fun whereAnd(sideLeft: String , conditionOperation: String , sideRight: String): QueryBuilder_conditionsGroup ;
    fun whereAnd(sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder_conditionsGroup ;


    fun whereOn(sideLeft: String , conditionOperation: String , sideRight: String): QueryBuilder_conditionsGroup ;
    fun whereOn(sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder_conditionsGroup ;


    fun whereOr(sideLeft: String , conditionOperation: String , sideRight: String): QueryBuilder_conditionsGroup ;
    fun whereOr(sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder_conditionsGroup ;


    fun whereIn(sideLeft: String , listValues: List<String>): QueryBuilder_conditionsGroup ;
    fun whereIn(sideLeft: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder_conditionsGroup;


    fun whereCondition(conditionLogical: String , sideLeft: String , conditionOperation: String , sideRight: String): QueryBuilder_conditionsGroup;
    fun whereCondition(conditionLogical: String ,sideLeft: String , conditionOperation: String , block: (QueryBuilder) -> QueryBuilder) : QueryBuilder_conditionsGroup;

}