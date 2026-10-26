package gog.my_project.data_base.query.builder.ast.select_builder.column_method.sum

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.sum.IQueryColumnMethodSumApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.sum.IQueryColumnMethodSumAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_over.QueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_over.QueryColumnOverBuilder

class QueryColumnMethodSumBuilder(
    override var ast: IQueryColumnMethodSumAst,
) : IQueryColumnMethodSumApi {

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodSumApi {
        val columnAst = QueryColumnsBaseAst()
        QueryColumnsBaseBuilder(columnAst).apply(blockColumn)
        ast.Column = columnAst
        return this
    }

    override fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodSumApi {
        val columnOverAst = QueryColumnOverAst()
        QueryColumnOverBuilder(columnOverAst).apply(blockOver)
        ast.Over = columnOverAst
        return this
    }

}
