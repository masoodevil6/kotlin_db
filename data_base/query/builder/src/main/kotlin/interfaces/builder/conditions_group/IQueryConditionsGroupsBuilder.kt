package gog.my_project.data_base.query.builder.interfaces.builder.conditions_group

import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.builder.interfaces.builder.conditions.IQueryConditionBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.conditions_item.IQueryConditionsBuilder

interface IQueryConditionsGroupsBuilder : IQueryConditionBuilder {

    var ast: IQueryConditionsGroupsAst;

    fun logical(logical: SqlLogical): IQueryConditionsGroupsBuilder;
    fun logicalAnd(): IQueryConditionsGroupsBuilder;
    fun logicalOr(): IQueryConditionsGroupsBuilder;
    fun logicalOn(): IQueryConditionsGroupsBuilder;

    fun addGroup(blockGroup: IQueryConditionsGroupsBuilder.() -> Unit): IQueryConditionsGroupsBuilder;

    fun addCondition(blockCondition: IQueryConditionsBuilder.() -> Unit): IQueryConditionsGroupsBuilder;

}