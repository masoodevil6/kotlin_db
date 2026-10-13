package gog.my_project.data_base.query.builder.ast.select_builder.withs_item

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
import gog.my_project.data_base.query.api.interfaces.cte.ICte
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.withs_item.IQueryWithsItemAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs_item.QueryWithsItemAst
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope

class QueryWithsItemBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IQueryWithsItemAst = QueryWithsItemAst(),
): IQueryWithsItemApi {

    private var parameterBindingScope = ParameterBindingScope()

    internal constructor(
        params: MutableList<SqlParameter<*>>,
        ast: IQueryWithsItemAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(params, ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    internal var selectedCte: Cte? = null
    internal var selectedCteNameOverride: String? = null




    override fun with(
        withName: String,
        withClass: ICte
    ): gog.my_project.data_base.query.api.interfaces.api.select_api.withs_item.IQueryWithsItemApi
    {

//        val cteName = with::class.findAnnotation<QBCte>()
//            ?: error("CteName annotation missing on ${with::class.simpleName}")
//        this.ast.withName = cteName.name

        selectedCte = null
        selectedCteNameOverride = null
        this.ast.withName = withName
        this.ast.withBody = withClass.cteQuery(params).ast

        return this;
    }

    override fun with(cte: Cte): IQueryWithsItemApi {
        require(cte.name.isNotBlank()) { "Cte name must not be blank" }
        selectedCte = cte
        selectedCteNameOverride = null
        ast.withName = cte.name
        ast.withBody = cte.definition.ast
        return this
    }

    override fun with(withName: String, cte: Cte): IQueryWithsItemApi {
        require(withName.isNotBlank()) { "WITH CTE name must not be blank" }
        selectedCte = cte
        selectedCteNameOverride = withName
        ast.withName = withName
        ast.withBody = cte.definition.ast
        return this
    }



    override fun with(
        withName: String,
        blockWith: IQueryRenderSelectApi.() -> Unit
    ): IQueryWithsItemApi {
        selectedCte = null
        selectedCteNameOverride = null
        this.ast.withName = withName;

        val ast = QueryRenderSelectAst();
        QueryRenderSelectBuilder(
            params,
            ast,
            parameterBindingScope,
        ).apply(blockWith);
        this.ast.withBody = ast;

        return this;
    }



}
