package gog.my_project.data_base.query.builder.interfaces.conditions

import gog.my_project.data_base.query.builder.interfaces.columns.IQueryToolsColumnsBase
import gog.my_project.data_base.query.definition.tools.enums.SqlConditionOperation
import gog.my_project.data_base.query.definition.tools.enums.SqlLogical

interface IQueryToolsConditions : IQueryToolsIsConditions {

    fun getConditionLogical(): SqlLogical?;
    fun getConditionSideLeft(): IQueryToolsColumnsBase?;
    fun getConditionOperation(): SqlConditionOperation?;
    fun getConditionSideRight(): Any?;

}