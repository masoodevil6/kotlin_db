package gog.my_project.data_base.query.builder.interfaces.conditions

import gog.my_project.data_base.query.definition.tools.enums.SqlLogical

interface IQueryToolsConditionsGroups :  IQueryToolsIsConditions {

    fun getGroupLogical(): SqlLogical?;
    fun getGroupConditions(): MutableList<IQueryToolsIsConditions>;

}