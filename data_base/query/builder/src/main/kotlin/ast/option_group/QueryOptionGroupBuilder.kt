package gog.my_project.data_base.query.builder.ast.option_group

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.column_base.IQueryColumnsBaseApi
import gog.my_project.data_base.query.api.interfaces.api.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.ast.interfaces.option_group.IQueryOptionGroupAst
import gog.my_project.data_base.query.ast.schema.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.option_group.QueryOptionGroupAst
import gog.my_project.data_base.query.builder.ast.column_base.QueryColumnsBaseBuilder

class QueryOptionGroupBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryOptionGroupAst = QueryOptionGroupAst(),
) : IQueryOptionGroupApi {


    override fun addColumn(
        blockColumn: IQueryColumnsBaseApi.() -> Unit
    ): IQueryOptionGroupApi
    {
        val ast = QueryColumnsBaseAst();
        QueryColumnsBaseBuilder(params ,ast).apply(blockColumn);
        this.ast.groupByList.add(ast);
        return this;
    }


}