package gog.my_project.data_base.query.builder.ast.select_builder.joins

import gog.my_project.data_base.query.api.interfaces.api.select_api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.joins_item.IQueryJoinsItemApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.joins.IQueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins_item.QueryJoinsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.joins_item.QueryJoinsItemBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext

class QueryJoinsBuilder(
    override var ast: IQueryJoinsAst = QueryJoinsAst(),
) : IQueryJoinsApi {

    private var parameterBindingScope = ParameterBindingScope()
    private var modelAliasContext = ModelTableAliasContext()
    private var querySourceContext = CteSourceContext()

    internal constructor(
        ast: IQueryJoinsAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this( ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal constructor(
        ast: IQueryJoinsAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
    ) : this(ast, parameterBindingScope) {
        this.modelAliasContext = modelAliasContext
    }

    internal constructor(
        ast: IQueryJoinsAst,
        parameterBindingScope: ParameterBindingScope,
        modelAliasContext: ModelTableAliasContext,
        querySourceContext: CteSourceContext,
    ) : this(ast, parameterBindingScope, modelAliasContext) {
        this.querySourceContext = querySourceContext
    }


    override fun addJoin(
        blockJoin: IQueryJoinsItemApi.() -> Unit
    ): IQueryJoinsApi
    {
        val ast = QueryJoinsItemAst();
        QueryJoinsItemBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockJoin);
        this.ast.joins.add(ast);
        return this;
    }

}
