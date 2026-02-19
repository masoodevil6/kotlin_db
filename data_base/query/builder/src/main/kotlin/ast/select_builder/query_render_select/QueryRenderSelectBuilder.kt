package gog.my_project.data_base.query.builder.ast.select_builder.query_render_select

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs.IQueryWithsApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_group.QueryOptionGroupAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_limit.QueryOptionLimitAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_offset.QueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_order.QueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.select.QuerySelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.builder.ast.select_builder.joins.QueryJoinsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_group.QueryOptionGroupBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_limit.QueryOptionLimitBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_offset.QueryOptionOffsetBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_order.QueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.select.QuerySelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.where.QueryWhereBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.withs.QueryWithsBuilder

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
        QueryWithsBuilder(
            params,
            ast
        ).apply(blockWiths);
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
        QuerySelectBuilder(
            params,
            ast
        ).apply(blockSelect);
        this.ast.select = ast;
        return this;
    }



    /* ==============================================================
      structure [table]
      ============================================================== */
    override fun table(
        blockTable: gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryTableAst();
        QueryTableBuilder(
            params,
            ast
        ).apply(blockTable);
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
        QueryJoinsBuilder(
            params,
            ast
        ).apply(blockJoins);
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
        QueryWhereBuilder(
            params,
            ast
        ).apply(blockGroup);
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
        QueryOptionLimitBuilder(
            params,
            ast
        ).apply(blockLimit)
        this.ast.optionLimit = ast;
        return this;
    }


    override fun offset(
        blockOffset: IQueryOptionOffsetApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOffsetAst();
        QueryOptionOffsetBuilder(
            params,
            ast
        ).apply(blockOffset);
        this.ast.optionOffset = ast;
        return this;
    }


    override fun group(
        blockGroup: IQueryOptionGroupApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        var ast = QueryOptionGroupAst();
        QueryOptionGroupBuilder(
            params,
            ast
        ).apply(blockGroup);
        this.ast.optionGroup = ast;
        return this;
    }


    override fun order(
        blockOrder: IQueryOptionOrderApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOrderAst();
        QueryOptionOrderBuilder(
            params,
            ast
        ).apply(blockOrder);
        this.ast.optionOrder = ast;
        return this;
    }


}