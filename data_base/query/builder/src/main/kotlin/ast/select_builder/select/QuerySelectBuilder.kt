package gog.my_project.data_base.query.builder.ast.select_builder.select

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.column.IQueryColumnsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.select.IQuerySelectApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.select.IQuerySelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.column.QueryColumnsAst
import gog.my_project.data_base.query.ast.schema.select_schema.select.QuerySelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.column.QueryColumnsBuilder

class QuerySelectBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQuerySelectAst = QuerySelectAst(),
) : IQuerySelectApi {

    override fun addColumn(
        blockColumn: IQueryColumnsApi.() -> Unit
    ): IQuerySelectApi
    {
        val ast = QueryColumnsAst();
        QueryColumnsBuilder(
            params,
            ast
        ).apply(blockColumn);
        this.ast.columns.add(ast);
        return this;
    }

}