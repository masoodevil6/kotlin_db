package gog.my_project.data_base.query.builder.ast.select_builder.withs

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs.IQueryWithsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.withs_item.QueryWithsItemBuilder

class QueryWithsBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryWithsAst = QueryWithsAst(),
) : IQueryWithsApi {

    override fun addWith(
        blockWith: IQueryWithsItemApi.() -> Unit
    ): IQueryWithsApi
    {
        val ast = QueryWithsItemAst();
        QueryWithsItemBuilder(
            params,
            ast
        ).apply(blockWith)
        this.ast.withs.add(ast);
        return this;
    }

}