package gog.my_project.data_base.query.builder.ast.query_render_select

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.api.interfaces.api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.api.interfaces.api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.api.interfaces.api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.withs.IQueryWithsApi
import gog.my_project.data_base.query.ast.interfaces.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.option_group.QueryOptionGroupAst
import gog.my_project.data_base.query.ast.schema.option_limit.QueryOptionLimitAst
import gog.my_project.data_base.query.ast.schema.option_offset.QueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.option_order.QueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select.QuerySelectAst
import gog.my_project.data_base.query.ast.schema.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.withs.QueryWithsAst
import gog.my_project.data_base.query.builder.ast.joins.QueryJoinsBuilder
import gog.my_project.data_base.query.builder.ast.option_group.QueryOptionGroupBuilder
import gog.my_project.data_base.query.builder.ast.option_limit.QueryOptionLimitBuilder
import gog.my_project.data_base.query.builder.ast.option_offset.QueryOptionOffsetBuilder
import gog.my_project.data_base.query.builder.ast.option_order.QueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.ast.select.QuerySelectBuilder
import gog.my_project.data_base.query.builder.ast.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.where.QueryWhereBuilder
import gog.my_project.data_base.query.builder.ast.withs.QueryWithsBuilder

class QueryRenderSelectBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryRenderSelectAst = QueryRenderSelectAst(),
) : IQueryRenderSelectApi
{


    /* ==============================================================
    structure [withs]
    ============================================================== */

    override fun withs(
        blockWiths: IQueryWithsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryWithsAst();
        QueryWithsBuilder(params ,ast).apply(blockWiths);
        this.ast.withs = ast;
        return this;
    }



    /* ==============================================================
      structure [select]
      ============================================================== */
    override fun select(
        blockSelect: IQuerySelectApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QuerySelectAst();
        QuerySelectBuilder(params ,ast).apply(blockSelect);
        this.ast.select = ast;
        return this;
    }



    /* ==============================================================
      structure [table]
      ============================================================== */
    override fun table(
        blockTable: IQueryTableApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryTableAst();
        QueryTableBuilder(params ,ast).apply(blockTable);
        this.ast.table = ast;
        return this;
    }



    /* ==============================================================
      structure [joins]
      ============================================================== */
    override fun joins(
        blockJoins: IQueryJoinsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryJoinsAst();
        QueryJoinsBuilder(params , ast).apply(blockJoins);
        this.ast.joins = ast;
        return this;
    }




    /* ==============================================================
      structure [where]
      ============================================================== */
    override fun where(
        blockGroup: IQueryWhereApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryWhereAst();
        QueryWhereBuilder(params , ast).apply(blockGroup);
        this.ast.where = ast;
        return this;
    }



    /* ==============================================================
      structure [options]
      ============================================================== */
    override fun limit(
        blockLimit: IQueryOptionLimitApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionLimitAst();
        QueryOptionLimitBuilder(params ,ast).apply(blockLimit);
        this.ast.optionLimit = ast;
        return this;
    }


    override fun offset(
        blockOffset: IQueryOptionOffsetApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOffsetAst();
        QueryOptionOffsetBuilder(params ,ast).apply(blockOffset);
        this.ast.optionOffset = ast;
        return this;
    }


    override fun group(
        blockGroup: IQueryOptionGroupApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        var ast = QueryOptionGroupAst();
        QueryOptionGroupBuilder(params ,ast).apply(blockGroup);
        this.ast.optionGroup = ast;
        return this;
    }


    override fun order(
        blockOrder: IQueryOptionOrderApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOrderAst();
        QueryOptionOrderBuilder(params , ast).apply(blockOrder);
        this.ast.optionOrder = ast;
        return this;
    }


}