package gog.my_project.data_base.query.builder.ast.select_builder.column_method.min

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.min.IQueryColumnMethodMinApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.min.IQueryColumnMethodMinAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_over.QueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_over.QueryColumnOverBuilder

class QueryColumnMethodMinBuilder(
    override var ast: IQueryColumnMethodMinAst,
) : IQueryColumnMethodMinApi {


    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodMinApi {
        val columnAst = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            columnAst
        ).apply(blockColumn);
        this.ast.Column =  columnAst;
        return this;
    }

    override fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodMinApi {
        val columnOverAst = QueryColumnOverAst()
        QueryColumnOverBuilder(columnOverAst).apply(blockOver)
        ast.Over = columnOverAst
        return this
    }

}