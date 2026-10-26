package gog.my_project.data_base.query.builder.ast.select_builder.where

import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.where.IQueryWhereAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.builder.ast.select_builder.conditions_group.QueryConditionsGroupsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext

class QueryWhereBuilder(
    override var ast: IQueryWhereAst = QueryWhereAst(),
) : IQueryWhereApi {

    private var parameterBindingScope = ParameterBindingScope()
    private var modelAliasContext = ModelTableAliasContext()
    private var querySourceContext = CteSourceContext()

    internal constructor(
        ast: IQueryWhereAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal constructor(
        ast: IQueryWhereAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
        querySourceContext: CteSourceContext,
    ) : this(ast, parameterBindingScope) {
        this.modelAliasContext = modelAliasContext
        this.querySourceContext = querySourceContext
    }


    override fun conditions(
        blockCondition: IQueryConditionsGroupsApi.() -> Unit
    ): IQueryWhereApi
    {
        val ast = QueryConditionsGroupsAst();
        QueryConditionsGroupsBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockCondition);
        this.ast.condition = ast;
        return this;
    }


}
