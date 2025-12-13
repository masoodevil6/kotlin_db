package gog.kotlin_db.data.base.query_builder.tools.where

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups

interface IQueryToolsWhere : IQueryTools {

    fun whereSetup(blockGroup: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsWhere;

}