package gog.my_project.data_base.query.builder.ast.joins

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.ast.interfaces.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.builder.ast.joins_item.QueryJoinsItemBuilder

class QueryJoinsBuilder(
    override var ast: IQueryJoinsAst = QueryJoinsAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) : IQueryJoinsApi {


    override fun addJoin(
        blockJoin: IQueryJoinsItemApi.() -> Unit
    ): IQueryJoinsApi
    {
        val ast = QueryJoinsItemAst();
        QueryJoinsItemBuilder(ast, params).apply(blockJoin);
        this.ast.joins.add(ast);
        return this;
    }

}