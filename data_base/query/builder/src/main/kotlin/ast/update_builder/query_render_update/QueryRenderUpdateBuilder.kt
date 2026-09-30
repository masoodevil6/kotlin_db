package gog.my_project.data_base.query.builder.ast.update_builder.query_render_update

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.column_update.IQueryColumnUpdateApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.interfaces.update_interface.query_render_update.IQueryRenderUpdateAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.query_render_update.QueryRenderUpdateAst
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.where.QueryWhereBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.column_update.QueryColumnUpdateBuilder

class QueryRenderUpdateBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryRenderUpdateAst = QueryRenderUpdateAst()
) : IQueryRenderUpdateApi {


    override fun addValue(blockColumn: IQueryColumnUpdateApi.() -> Unit): IQueryRenderUpdateApi {
        val ast = QueryColumnUpdateAst();
        QueryColumnUpdateBuilder(
            params,
            ast
        ).apply(blockColumn);
        this.ast.columns.add(ast);
        return this;
    }


    override fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderUpdateApi {
        val ast = QueryTableAst();
        QueryTableBuilder(
            params,
            ast
        ).apply(blockTable);
        this.ast.table = ast;
        return this;
    }


    override fun where(blockGroup: IQueryWhereApi.() -> Unit): IQueryRenderUpdateApi {
        val ast = QueryWhereAst();
        QueryWhereBuilder(
            params,
            ast
        ).apply(blockGroup);
        this.ast.where = ast;
        return this;
    }

}