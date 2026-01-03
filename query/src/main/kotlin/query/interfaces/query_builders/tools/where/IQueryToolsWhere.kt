package gog.my_project.query.interfaces.query_builders.tools.where

import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups


interface IQueryToolsWhere : IQueryTools {

    var params: MutableList<Any?>

    fun getGroupCondition(): IQueryToolsConditionsGroups?;

    fun whereSetup(blockGroup: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsWhere;

}