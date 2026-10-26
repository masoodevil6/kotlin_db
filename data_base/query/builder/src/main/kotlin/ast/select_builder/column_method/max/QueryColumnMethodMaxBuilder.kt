package gog.my_project.data_base.query.builder.ast.select_builder.column_method.max

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.max.IQueryColumnMethodMaxApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.max.IQueryColumnMethodMaxAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_over.QueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_over.QueryColumnOverBuilder

class QueryColumnMethodMaxBuilder(
    override var ast: IQueryColumnMethodMaxAst
) : IQueryColumnMethodMaxApi  {

    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodMaxApi {
        val columnAst = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            columnAst
        ).apply(blockColumn);
        this.ast.Column =  columnAst;
        return this;
    }

    override fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodMaxApi {
        val columnOverAst = QueryColumnOverAst()
        QueryColumnOverBuilder(columnOverAst).apply(blockOver)
        ast.Over = columnOverAst
        return this
    }

}