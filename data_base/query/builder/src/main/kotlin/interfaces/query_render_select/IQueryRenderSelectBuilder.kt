package gog.my_project.data_base.query.builder.interfaces.query_render_select

import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.interfaces.IQuery
import gog.my_project.data_base.query.builder.interfaces.joins.IQueryJoinsBuilder
import gog.my_project.data_base.query.builder.interfaces.option_group.IQueryOptionGroupBuilder
import gog.my_project.data_base.query.builder.interfaces.option_limit.IQueryOptionLimitBuilder
import gog.my_project.data_base.query.builder.interfaces.option_offset.IQueryOptionOffsetBuilder
import gog.my_project.data_base.query.builder.interfaces.option_order.IQueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.interfaces.select.IQuerySelectBuilder
import gog.my_project.data_base.query.builder.interfaces.table.IQueryTableBuilder
import gog.my_project.data_base.query.builder.interfaces.where.IQueryWhereBuilder
import gog.my_project.data_base.query.builder.interfaces.withs.IQueryWithsBuilder

interface IQueryRenderSelectBuilder : IQuery {

    var ast: IQueryRenderSelectAst;

    fun withs(blockWiths: IQueryWithsBuilder.() -> Unit): IQueryRenderSelectBuilder;
    fun select(blockSelect: IQuerySelectBuilder.() -> Unit): IQueryRenderSelectBuilder;
    fun table(blockTable: IQueryTableBuilder.() -> Unit): IQueryRenderSelectBuilder;
    fun joins(blockJoins: IQueryJoinsBuilder.() -> Unit): IQueryRenderSelectBuilder;
    fun where(blockGroup: IQueryWhereBuilder.() -> Unit): IQueryRenderSelectBuilder;

    //    fun pageInit(optionLimit: Long , optionOffset: Long) : IQueryBuilder;
//    fun limit(optionLimit: Long) : IQueryBuilder;
//    fun offset(optionOffset: Long) : IQueryBuilder;
//
    fun limit(blockLimit: IQueryOptionLimitBuilder.() -> Unit) : IQueryRenderSelectBuilder ;
    fun offset(blockOffset: IQueryOptionOffsetBuilder.() -> Unit) : IQueryRenderSelectBuilder ;

    fun group(blockGroup: IQueryOptionGroupBuilder.() -> Unit): IQueryRenderSelectBuilder;

    fun order(blockOrder: IQueryOptionOrderBuilder.() -> Unit): IQueryRenderSelectBuilder;

}