package gog.my_project.data_base.query.api.interfaces.query_render_select

import gog.my_project.data_base.query.api.interfaces.IQueryApi
import gog.my_project.data_base.query.api.interfaces.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.api.interfaces.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.api.interfaces.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.api.interfaces.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.withs.IQueryWithsApi
import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst

interface IQueryRenderSelectApi : IQueryApi {

    var ast: IQueryRenderSelectAst;

    fun withs(blockWiths: IQueryWithsApi.() -> Unit): IQueryRenderSelectApi;
    fun select(blockSelect: IQuerySelectApi.() -> Unit): IQueryRenderSelectApi;
    fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderSelectApi;
    fun joins(blockJoins: IQueryJoinsApi.() -> Unit): IQueryRenderSelectApi;
    fun where(blockGroup: IQueryWhereApi.() -> Unit): IQueryRenderSelectApi;

    //    fun pageInit(optionLimit: Long , optionOffset: Long) : IQueryApi;
//    fun limit(optionLimit: Long) : IQueryApi;
//    fun offset(optionOffset: Long) : IQueryApi;
//
    fun limit(blockLimit: IQueryOptionLimitApi.() -> Unit) : IQueryRenderSelectApi;
    fun offset(blockOffset: IQueryOptionOffsetApi.() -> Unit) : IQueryRenderSelectApi;

    fun group(blockGroup: IQueryOptionGroupApi.() -> Unit): IQueryRenderSelectApi;

    fun order(blockOrder: IQueryOptionOrderApi.() -> Unit): IQueryRenderSelectApi;
}