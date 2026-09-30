package gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.ast.interfaces.delete_interface.query_render_delete.IQueryRenderDeleteAst
import gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete.QueryRenderDeleteAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.where.QueryWhereBuilder

class QueryRenderDeleteBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryRenderDeleteAst = QueryRenderDeleteAst(),
) : IQueryRenderDeleteApi {



    override fun addTarget(
        target: String
    ): IQueryRenderDeleteApi {
        this.ast.targets.add(target);
        return this;
    }


    override fun table(
        blockTable: IQueryTableApi.() -> Unit
    ): IQueryRenderDeleteApi {
        val ast = QueryTableAst();
        QueryTableBuilder(
            params,
            ast
        ).apply(blockTable);
        this.ast.table = ast;
        return this;
    }

    override fun where(
        blockGroup: IQueryWhereApi.() -> Unit
    ): IQueryRenderDeleteApi {
        val ast = QueryWhereAst();
        QueryWhereBuilder(
            params,
            ast
        ).apply(blockGroup);
        this.ast.where = ast;
        return this;
    }

}