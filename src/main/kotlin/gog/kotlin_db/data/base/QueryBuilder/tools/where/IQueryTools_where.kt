package gog.kotlin_db.data.base.QueryBuilder.tools.where

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroups

interface IQueryTools_where : IQueryTools {

    fun whereSetup(blockGroup: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_where;

}