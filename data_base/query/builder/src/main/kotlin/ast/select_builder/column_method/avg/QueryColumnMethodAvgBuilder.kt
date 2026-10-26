package gog.my_project.data_base.query.builder.ast.select_builder.column_method.avg

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.avg.IQueryColumnMethodAvgApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.avg.IQueryColumnMethodAvgAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_over.QueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_over.QueryColumnOverBuilder

class QueryColumnMethodAvgBuilder(
    override var ast: IQueryColumnMethodAvgAst,
) : IQueryColumnMethodAvgApi {

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodAvgApi {
        val columnAst = QueryColumnsBaseAst()
        QueryColumnsBaseBuilder(columnAst).apply(blockColumn)
        ast.Column = columnAst
        return this
    }

    override fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodAvgApi {
        val columnOverAst = QueryColumnOverAst()
        QueryColumnOverBuilder(columnOverAst).apply(blockOver)
        ast.Over = columnOverAst
        return this
    }

}
