package gog.my_project.data_base.query.builder.ast.select_builder.conditions_group

import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_group.IQueryConditionsGroupsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.conditions_item.IQueryConditionsApi
import gog.my_project.data_base.query.api.tools.enums.SqlLogical
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.builder.ast.select_builder.conditions_item.QueryConditionsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext

class QueryConditionsGroupsBuilder(
    override var ast: IQueryConditionsGroupsAst = QueryConditionsGroupsAst(),
): IQueryConditionsGroupsApi {

    private var parameterBindingScope = ParameterBindingScope()
    private var modelAliasContext = ModelTableAliasContext()
    private var querySourceContext = CteSourceContext()

    internal constructor(
        ast: IQueryConditionsGroupsAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this( ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal constructor(
        ast: IQueryConditionsGroupsAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
        querySourceContext: CteSourceContext,
    ) : this(ast, parameterBindingScope) {
        this.modelAliasContext = modelAliasContext
        this.querySourceContext = querySourceContext
    }


    /* ==============================================================
    structure [logical]
    ============================================================== */

    override fun logical(
        logical: SqlLogical
    ): IQueryConditionsGroupsApi
    {
        this.ast.metadata.conditionLogical = logical.value;
        return this;
    }

    override fun logicalAnd(): IQueryConditionsGroupsApi {
        return this.logical(SqlLogical.And)
    }

    override fun logicalOr(): IQueryConditionsGroupsApi {
        return this.logical(SqlLogical.Or)
    }

    override fun logicalOn(): IQueryConditionsGroupsApi {
        return this.logical(SqlLogical.On)
    }



    /* ==============================================================
    structure [logical]
    ============================================================== */
    override fun addGroup(
        blockGroup: IQueryConditionsGroupsApi.() -> Unit
    ): IQueryConditionsGroupsApi
    {
        val ast = QueryConditionsGroupsAst();
        QueryConditionsGroupsBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockGroup)
        this.ast.conditions.add(ast);
        return this;
    }

    override fun addCondition(
        blockCondition: IQueryConditionsApi.() -> Unit
    ): IQueryConditionsGroupsApi
    {
        var ast = QueryConditionsAst();
        QueryConditionsBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockCondition)
        this.ast.conditions.add(ast);
        return this;
    }

}
