package gog.my_project.data_base.query.builder.ast.select_builder.column_method.max

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.min.IQueryColumnMethodMinApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder

class QueryColumnMethodMaxAstBuilder(
    override var ast: IQueryColumnMethodMinAst
) : IQueryColumnMethodMinApi  {

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodMinApi {
        val columnAst = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            columnAst
        ).apply(blockColumn);
        this.ast.Column =  columnAst;
        return this;
    }

}