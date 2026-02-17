package gog.my_project.data_base.query.builder.interfaces.builder.query_render_select

import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.interfaces.builder.IQuery
import gog.my_project.data_base.query.builder.interfaces.builder.joins.IQueryJoinsBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.option_group.IQueryOptionGroupBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.option_limit.IQueryOptionLimitBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.option_offset.IQueryOptionOffsetBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.option_order.IQueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.select.IQuerySelectBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.table.IQueryTableBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.where.IQueryWhereBuilder
import gog.my_project.data_base.query.builder.interfaces.builder.withs.IQueryWithsBuilder

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