package gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.insert_api.column_insert.IQueryColumnInsertApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.ast.interfaces.insert_interface.query_render_insert.IQueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
import gog.my_project.data_base.query.builder.ast.insert_builder.column_insert.QueryColumnInsertBuilder

class QueryRenderInsertBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryRenderInsertAst = QueryRenderInsertAst()
): IQueryRenderInsertApi{


    override fun table(
        tableName: String
    ): IQueryRenderInsertApi {
        ast.table = tableName;
        return this;
    }

    override fun addColumn(
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