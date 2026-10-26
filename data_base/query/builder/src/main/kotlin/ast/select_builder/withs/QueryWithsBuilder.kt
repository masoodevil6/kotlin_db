package gog.my_project.data_base.query.builder.ast.select_builder.withs

import gog.my_project.data_base.query.api.interfaces.api.select_api.withs.IQueryWithsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs.IQueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.withs_item.QueryWithsItemBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope

class QueryWithsBuilder(
    override var ast: IQueryWithsAst = QueryWithsAst(),
) : IQueryWithsApi {

    private var parameterBindingScope = ParameterBindingScope()

    internal constructor(
        ast: IQueryWithsAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal val cteRoots = mutableListOf<Cte>()
    internal val cteRootItems = mutableListOf<QueryWithsItemAst>()
    internal val cteNameOverrides = java.util.IdentityHashMap<Cte, String>()

    override fun addWith(
        blockWith: IQueryWithsItemApi.() -> Unit
    ): IQueryWithsApi
    {
        val ast = QueryWithsItemAst();
        val itemBuilder = QueryWithsItemBuilder(
            ast,
            parameterBindingScope,
        ).apply(blockWith)
        val cte = itemBuilder.selectedCte
        this.ast.withs.add(ast)
        if (cte != null) {
            cteRoots.add(cte)
            cteRootItems.add(ast)
            itemBuilder.selectedCteNameOverride?.let { withName ->
                val previousName = cteNameOverrides[cte]
                require(previousName == null || previousName == withName) {
                    "The same Cte cannot be assigned multiple WITH names: $previousName and $withName"
                }
                cteNameOverrides[cte] = withName
            }
        }
        return this;
    }

}
