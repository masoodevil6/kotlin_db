package gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select

import gog.my_project.data_base.query.api.interfaces.api.IQueryApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs.IQueryWithsApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst


interface IQueryRenderSelectApi : IQueryApi {

    var ast: IQueryRenderSelectAst;

    fun withs(blockWiths: IQueryWithsApi.() -> Unit): IQueryRenderSelectApi;
    fun select(blockSelect: IQuerySelectApi.() -> Unit): IQueryRenderSelectApi;
    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderSelectApi;
    fun joins(blockJoins: IQueryJoinsApi.() -> Unit): IQueryRenderSelectApi;
    fun where(blockGroup: IQueryWhereApi.() -> Unit): IQueryRenderSelectApi;

    fun limit(blockLimit: IQueryOptionLimitApi.() -> Unit) : IQueryRenderSelectApi;
    fun offset(blockOffset: IQueryOptionOffsetApi.() -> Unit) : IQueryRenderSelectApi;

    fun group(blockGroup: IQueryOptionGroupApi.() -> Unit): IQueryRenderSelectApi;

    fun order(blockOrder: IQueryOptionOrderApi.() -> Unit): IQueryRenderSelectApi;
}