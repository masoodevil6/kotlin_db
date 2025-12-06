package gog.kotlin_db.data.base.QueryBuilder.tools.where


import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroup


interface IQueryTools_where : IQueryTools {

    fun where(blockGroup: (QueryTools_conditionsGroup) -> QueryTools_conditionsGroup): QueryTools_where;

}