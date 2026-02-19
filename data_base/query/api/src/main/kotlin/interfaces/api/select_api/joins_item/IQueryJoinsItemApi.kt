package gog.my_project.data_base.query.api.interfaces.api.select_api.joins_item

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.tools.enums.SqlTypeJoin
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins_item.IQueryJoinsItemAst

interface IQueryJoinsItemApi : IQueryApi {

    var ast: IQueryJoinsItemAst;

    fun typeJoin(type: SqlTypeJoin): IQueryJoinsItemApi;
    fun innerJoin(): IQueryJoinsItemApi;
    fun leftJoin(): IQueryJoinsItemApi;
    fun rightJoin(): IQueryJoinsItemApi;

    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryJoinsItemApi;

    fun condition(blockCondition: IQueryConditionsGroupsApi.() -> Unit): IQueryJoinsItemApi;

}