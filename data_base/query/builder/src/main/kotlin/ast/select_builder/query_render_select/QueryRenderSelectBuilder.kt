package gog.my_project.data_base.query.builder.ast.select_builder.query_render_select

import gog.my_project.data_base.query.api.interfaces.api.select_api.joins.IQueryJoinsApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_group.IQueryOptionGroupApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_limit.IQueryOptionLimitApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_offset.IQueryOptionOffsetApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.option_order.IQueryOptionOrderApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.select.IQuerySelectApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.withs.IQueryWithsApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import kotlin.reflect.KClass
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.joins.QueryJoinsAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_group.QueryOptionGroupAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_limit.QueryOptionLimitAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_offset.QueryOptionOffsetAst
import gog.my_project.data_base.query.ast.schema.select_schema.option_order.QueryOptionOrderAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.ast.schema.select_schema.select.QuerySelectAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.withs.QueryWithsAst
import gog.my_project.data_base.query.builder.ast.select_builder.joins.QueryJoinsBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteAstComposer
import gog.my_project.data_base.query.builder.ast.select_builder.option_group.QueryOptionGroupBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_limit.QueryOptionLimitBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_offset.QueryOptionOffsetBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.option_order.QueryOptionOrderBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.select.QuerySelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.table.QueryTableBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.where.QueryWhereBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.withs.QueryWithsBuilder

class QueryRenderSelectBuilder(
    override var ast: IQueryRenderSelectAst = QueryRenderSelectAst(),
) : IQueryRenderSelectApi
{

    internal var parameterBindingScope = ParameterBindingScope()
        private set

    internal constructor(
        ast: IQueryRenderSelectAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(ast) {
        this.parameterBindingScope = parameterBindingScope
    }

    private val modelAliasContext = ModelTableAliasContext()
    private val querySourceContext = CteSourceContext()
    private val cteAstComposer = CteAstComposer(ast, modelAliasContext, querySourceContext)
    private var queryDefinitionName: String? = null
    private var queryDeclarationOwner: KClass<*>? = null

    internal fun requireCteOutputAliases(name: String, declarationOwner: KClass<*>? = null) {
        queryDefinitionName = name
        queryDeclarationOwner = declarationOwner
    }

    internal fun cteDependencies(): List<Cte> = cteAstComposer.dependencies()


    /* ==============================================================
    structure [withs]
    ============================================================== */

    override fun withs(
        blockWiths: IQueryWithsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryWithsAst();
        val withsBuilder = QueryWithsBuilder(
            ast,
            parameterBindingScope,
        ).apply(blockWiths);
        this.ast.withs = ast;
        cteAstComposer.setExplicitWiths(ast, withsBuilder)
        return this;
    }



    /* ==============================================================
      structure [select]
      ============================================================== */
    override fun select(
        blockSelect: IQuerySelectApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QuerySelectAst();
        QuerySelectBuilder(
            ast,
            modelAliasContext,
            querySourceContext,
            queryDefinitionName,
            queryDeclarationOwner,
        ).apply(blockSelect);
        this.ast.select = ast;
        return this;
    }

    /* ==============================================================
      structure [from]
      ============================================================== */
    override fun from(
        blockTable: gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        check(querySourceContext.snapshot() == null) { "A Cte source is already configured" }
        cteAstComposer.clearNamedCteSource()
        this.ast.table?.let(modelAliasContext::unregisterNamedSource)
        val ast = QueryTableAst();
        val tableBuilder = QueryTableBuilder(
            ast,
            modelAliasContext,
            allowSelectQuerySources = true,
            parameterBindingScope = parameterBindingScope,
        ).apply(blockTable);
        val selectedNamedCte = tableBuilder.selectedCteSource
        if (selectedNamedCte != null) {
            cteAstComposer.addInlineCteSource(selectedNamedCte, tableBuilder.selectedOccurrenceAlias)
            return this
        }
        this.ast.table = ast;
        if (ast.metadata.cteRuntimeName != null && !ast.metadata.isCteObjectSource) {
            cteAstComposer.registerNamedCteSource(ast)
        }
        return this;
    }



    /* ==============================================================
      structure [joins]
      ============================================================== */
    override fun joins(
        blockJoins: IQueryJoinsApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryJoinsAst();
        QueryJoinsBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockJoins);
        this.ast.joins = ast;
        return this;
    }




    /* ==============================================================
      structure [where]
      ============================================================== */
    override fun where(
        blockGroup: IQueryWhereApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryWhereAst();
        QueryWhereBuilder(
            ast,
            parameterBindingScope,
            modelAliasContext,
            querySourceContext,
        ).apply(blockGroup);
        this.ast.where = ast;
        return this;
    }



    /* ==============================================================
      structure [options]
      ============================================================== */
    override fun limit(
        blockLimit: IQueryOptionLimitApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionLimitAst();
        QueryOptionLimitBuilder(

            ast
        ).apply(blockLimit)
        this.ast.optionLimit = ast;
        return this;
    }


    override fun offset(
        blockOffset: IQueryOptionOffsetApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOffsetAst();
        QueryOptionOffsetBuilder(
            ast
        ).apply(blockOffset);
        this.ast.optionOffset = ast;
        return this;
    }


    override fun group(
        blockGroup: IQueryOptionGroupApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        var ast = QueryOptionGroupAst();
        QueryOptionGroupBuilder(
            ast
        ).apply(blockGroup);
        this.ast.optionGroup = ast;
        return this;
    }


    override fun order(
        blockOrder: IQueryOptionOrderApi.() -> Unit
    ): IQueryRenderSelectApi
    {
        val ast = QueryOptionOrderAst();
        QueryOptionOrderBuilder(
            ast
        ).apply(blockOrder);
        this.ast.optionOrder = ast;
        return this;
    }


}
