package gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.builder.ast.insert_builder.column_insert.QueryColumnInsertBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder

class QueryRenderInsertBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryRenderInsertAst = QueryRenderInsertAst()
): IQueryRenderInsertApi{


    override fun table(blockTable: IQueryTableApi.() -> Unit): IQueryRenderInsertApi {
        val ast = QueryTableAst();
        QueryTableBuilder(
            params,
            ast
        ).apply(blockTable);
        this.ast.table = ast;
        return this;
    }

    override fun addValue(
        blockColumn: IQueryColumnInsertApi.() -> Unit
    ): IQueryRenderInsertApi {
        val ast = QueryColumnInsertAst();
        QueryColumnInsertBuilder(
            params,
            ast
        ).apply(blockColumn);
        this.ast.columns.add(ast);
        return this;
    }




}