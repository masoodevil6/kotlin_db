package gog.kotlin_db.data.base.query_builder.tools.join

import gog.kotlin_db.data.base.query_builder.tools.IQueryTools
import gog.kotlin_db.data.base.query_builder.tools.conditions.QueryConditionGroups.QueryToolsConditionsGroups

interface IQueryToolsJoinsConnect : IQueryTools {

    fun setupJoins(blockJoin: (IQueryToolsJoinsConnect) -> QueryToolsJoinsConnect): QueryToolsJoinsConnect;

    fun join(tableName: String , tableAlias: String , blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsJoinsConnect;
    fun join(tableName: String, blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsJoinsConnect;

    fun leftJoin(tableName: String , tableAlias: String , blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsJoinsConnect;
    fun leftJoin(tableName: String, blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsJoinsConnect;

    fun rightJoin(tableName: String , tableAlias: String , blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsJoinsConnect;
    fun rightJoin(tableName: String, blockCondition: (QueryToolsConditionsGroups) -> QueryToolsConditionsGroups): QueryToolsJoinsConnect;


}