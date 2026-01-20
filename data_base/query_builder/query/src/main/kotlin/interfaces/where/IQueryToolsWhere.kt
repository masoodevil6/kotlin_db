package gog.my_project.data_base.query_builder.query.interfaces.where

import gog.my_project.data_base.query_builder.query.interfaces.IQueryTools
import gog.my_project.data_base.query_builder.query.interfaces.conditions.IQueryToolsConditionsGroups

interface IQueryToolsWhere : IQueryTools {

    fun getGroupCondition(): IQueryToolsConditionsGroups?;

    fun conditions(blockCondition: IQueryToolsConditionsGroups.() -> IQueryToolsConditionsGroups): IQueryToolsWhere;

}