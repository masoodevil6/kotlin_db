package gog.my_project.data_base.query.api.interfaces.joins_item

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.table.IQueryTableApi
import gog.my_project.data_base.query.api.tools.enums.SqlTypeJoin
import gog.my_project.data_base.query.ast.interfaces.joins_item.IQueryJoinsItemAst

interface IQueryJoinsItemApi : IQueryApi {

    var ast: IQueryJoinsItemAst;

    fun typeJoin(type: SqlTypeJoin): IQueryJoinsItemApi;
    fun innerJoin(): IQueryJoinsItemApi;
    fun leftJoin(): IQueryJoinsItemApi;
    fun rightJoin(): IQueryJoinsItemApi;

    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryJoinsItemApi;

    fun condition(blockCondition: IQueryConditionsGroupsApi.() -> Unit): IQueryJoinsItemApi;

}