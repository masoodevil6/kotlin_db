package gog.my_project.data_base.query.builder.ast.option_group

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.schema.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.option_group.QueryOptionGroupAst
import gog.my_project.data_base.query.builder.ast.column_base.QueryColumnsBaseBuilder

class QueryOptionGroupBuilder(
    override var ast: IQueryOptionGroupAst = QueryOptionGroupAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) : IQueryOptionGroupApi {


    override fun addColumn(
        blockColumn: IQueryColumnsBaseApi.() -> Unit
    ): IQueryOptionGroupApi
    {
        val ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(ast, params).apply(blockColumn);
        this.ast.groupByList.add(ast);
        return this;
    }


}