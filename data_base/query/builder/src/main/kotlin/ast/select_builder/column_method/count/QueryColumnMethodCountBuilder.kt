package gog.my_project.data_base.query.builder.ast.select_builder.column_method.count

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.count.IQueryColumnMethodCountApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.count.IQueryColumnMethodCountAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.count.QueryColumnMethodCountAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_over.QueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_over.QueryColumnOverBuilder

class QueryColumnMethodCountBuilder(
    override var ast: IQueryColumnMethodCountAst = QueryColumnMethodCountAst()
) : IQueryColumnMethodCountApi {

    override fun distinct(isDistinct: Boolean): IQueryColumnMethodCountApi {
        this.ast.metadata.isDistinct = isDistinct;
        return this;
    }

    override fun addColumn(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodCountApi {
        val columnAst = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            columnAst
        ).apply(blockColumn);
        this.ast.columns.add(columnAst)
        return this;
    }

    override fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodCountApi {
        val columnOverAst = QueryColumnOverAst()
        QueryColumnOverBuilder(columnOverAst).apply(blockOver)
        ast.Over = columnOverAst
        return this
    }

}