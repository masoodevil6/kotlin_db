package gog.my_project.data_base.query.builder.interfaces.where

import gog.my_project.data_base.query.builder.interfaces.IQueryTools
import gog.my_project.data_base.query.builder.interfaces.conditions.IQueryToolsConditionsGroups

interface IQueryToolsWhere : IQueryTools {

    fun getGroupCondition(): IQueryToolsConditionsGroups?;

}