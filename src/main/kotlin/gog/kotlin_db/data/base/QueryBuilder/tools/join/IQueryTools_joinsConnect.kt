package gog.kotlin_db.data.base.QueryBuilder.tools.join

import gog.kotlin_db.data.base.QueryBuilder.tools.IQueryTools
import gog.kotlin_db.data.base.QueryBuilder.tools.conditions.QueryConditionGroups.QueryTools_conditionsGroups

interface IQueryTools_joinsConnect : IQueryTools {

    fun setupJoins(blockJoin: (IQueryTools_joinsConnect) -> QueryTools_joinsConnect): QueryTools_joinsConnect;

    fun join(tableName: String , tableAlias: String , blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_joinsConnect;
    fun join(tableName: String, blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_joinsConnect;

    fun leftJoin(tableName: String , tableAlias: String , blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_joinsConnect;
    fun leftJoin(tableName: String, blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_joinsConnect;

    fun rightJoin(tableName: String , tableAlias: String , blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_joinsConnect;
    fun rightJoin(tableName: String, blockCondition: (QueryTools_conditionsGroups) -> QueryTools_conditionsGroups): QueryTools_joinsConnect;


}