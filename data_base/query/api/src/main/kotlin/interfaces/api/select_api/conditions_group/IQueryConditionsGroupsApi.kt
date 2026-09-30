package gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_group

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item.IQueryConditionsApi
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst


interface IQueryConditionsGroupsApi : IQueryApi<IQueryConditionsGroupsAst> {

    fun logical(logical: SqlLogical): IQueryConditionsGroupsApi;
    fun logicalAnd(): IQueryConditionsGroupsApi;
    fun logicalOr(): IQueryConditionsGroupsApi;
    fun logicalOn(): IQueryConditionsGroupsApi;

    fun addGroup(blockGroup: IQueryConditionsGroupsApi.() -> Unit): IQueryConditionsGroupsApi;

    fun addCondition(blockCondition: IQueryConditionsApi.() -> Unit): IQueryConditionsGroupsApi;


}