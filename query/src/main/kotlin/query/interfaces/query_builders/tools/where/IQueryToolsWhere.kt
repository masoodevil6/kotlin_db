package gog.my_project.query.interfaces.query_builders.tools.where

import gog.my_project.datas.SqlParameter
import gog.my_project.query.interfaces.query_builders.tools.IQueryTools
import gog.my_project.query.interfaces.query_builders.tools.conditions.IQueryToolsConditionsGroups


interface IQueryToolsWhere : IQueryTools {

    fun getGroupCondition(): IQueryToolsConditionsGroups?;

    fun conditions(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsWhere;

}