package gog.my_project.data_base.query.builder.ast.where

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.where.IQueryWhereApi
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.where.QueryWhereAst
import gog.my_project.data_base.query.builder.ast.conditions_group.QueryConditionsGroupsBuilder

class QueryWhereBuilder(
    override var ast: IQueryWhereAst = QueryWhereAst(),
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>()
) : IQueryWhereApi {


    override fun conditions(
        blockCondition: IQueryConditionsGroupsApi.() -> Unit
    ): IQueryWhereApi
    {
        val ast = QueryConditionsGroupsAst();
        QueryConditionsGroupsBuilder(ast, params).apply(blockCondition);
        this.ast.condition = ast;
        return this;
    }


}