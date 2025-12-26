package gog.my_project.query.interfaces.query_builders.tools.join

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups


interface IQueryToolsJoinsConnect : IQueryTools {

    fun setupJoins(blockJoin: (IQueryToolsJoinsConnect) -> IQueryToolsJoinsConnect): IQueryToolsJoinsConnect;

    fun join(tableName: String , tableAlias: String , blockCondition: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsJoinsConnect;
    fun join(tableName: String, blockCondition: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsJoinsConnect;

    fun leftJoin(tableName: String , tableAlias: String , blockCondition: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsJoinsConnect;
    fun leftJoin(tableName: String, blockCondition: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsJoinsConnect;

    fun rightJoin(tableName: String , tableAlias: String , blockCondition: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsJoinsConnect;
    fun rightJoin(tableName: String, blockCondition: (IQueryToolsConditionsGroups) -> IQueryToolsConditionsGroups): IQueryToolsJoinsConnect;

}