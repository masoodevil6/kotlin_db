package gog.my_project.data_base.query.builder.ast.select_builder.joins

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.joins_item.QueryJoinsItemBuilder

class QueryJoinsBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryJoinsAst = QueryJoinsAst(),
) : IQueryJoinsApi {


    override fun addJoin(
        blockJoin: IQueryJoinsItemApi.() -> Unit
    ): IQueryJoinsApi
    {
        val ast = QueryJoinsItemAst();
        QueryJoinsItemBuilder(
            params,
            ast
        ).apply(blockJoin);
        this.ast.joins.add(ast);
        return this;
    }

}