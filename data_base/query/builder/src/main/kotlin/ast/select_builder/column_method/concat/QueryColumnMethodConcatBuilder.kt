package gog.my_project.data_base.query.builder.ast.select_builder.column_method.concat

import gog.my_project.data_base.query.api.interfaces.api.select_api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_method.concat.IQueryColumnMethodConcatApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.column_over.IQueryColumnOverApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.column_method.concat.IQueryColumnMethodConcatAstPart
import gog.my_project.data_base.query.ast.schema.select_schema.column_method.concat.QueryColumnMethodConcatAst
import gog.my_project.data_base.query.ast.schema.select_schema.column_over.QueryColumnOverAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.builder.ast.select_builder.column_base.QueryColumnsBaseBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.column_over.QueryColumnOverBuilder


class QueryColumnMethodConcatBuilder(
    override var ast: IQueryColumnMethodConcatAst = QueryColumnMethodConcatAst()
) : IQueryColumnMethodConcatApi {


    override fun column(blockColumn: IQueryColumnsBaseApi.() -> Unit): IQueryColumnMethodConcatApi {
        val columnAst = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(
            columnAst
        ).apply(blockColumn);
        this.ast.parts.add(
            IQueryColumnMethodConcatAstPart.Column(columnAst)
        )
        return this;
    }

    override fun string(str: String): IQueryColumnMethodConcatApi {
        this.ast.parts.add(
            IQueryColumnMethodConcatAstPart.Text(str)
        )
        return this
    }



    override fun over(blockOver: IQueryColumnOverApi.() -> Unit): IQueryColumnMethodConcatApi {
        val columnOverAst = QueryColumnOverAst()
        QueryColumnOverBuilder(columnOverAst).apply(blockOver)
        ast.Over = columnOverAst
        return this
    }


}