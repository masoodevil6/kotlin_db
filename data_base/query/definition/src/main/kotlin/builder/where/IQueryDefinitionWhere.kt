package gog.my_project.data_base.query.definition.interfaces.where

import gog.my_project.data_base.query.definition.interfaces.conditions.IQueryDefinitionConditionsGroups

interface IQueryDefinitionWhere {

    fun conditions(blockCondition: IQueryDefinitionConditionsGroups.() -> IQueryDefinitionConditionsGroups): IQueryDefinitionWhere;


}