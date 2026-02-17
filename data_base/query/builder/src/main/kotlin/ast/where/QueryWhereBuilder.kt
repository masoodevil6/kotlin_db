package gog.my_project.data_base.query.builder.ast.where

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.where.IQueryWhereApi
import gog.my_project.data_base.query.ast.interfaces.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.where.QueryWhereAst
import gog.my_project.data_base.query.builder.ast.conditions_group.QueryConditionsGroupsBuilder

class QueryWhereBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryWhereAst = QueryWhereAst(),
) : IQueryWhereApi {


    override fun conditions(
        blockCondition: IQueryConditionsGroupsApi.() -> Unit
    ): IQueryWhereApi
    {
        val ast = QueryConditionsGroupsAst();
        QueryConditionsGroupsBuilder(params ,ast).apply(blockCondition);
        this.ast.condition = ast;
        return this;
    }


}