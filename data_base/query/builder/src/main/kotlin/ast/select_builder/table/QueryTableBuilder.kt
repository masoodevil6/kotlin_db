package gog.my_project.data_base.query.builder.ast.select_builder.table

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.metadata.models.ModelMetadataResolver
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import kotlin.reflect.KClass

class QueryTableBuilder internal constructor(
    override var ast: IQueryTableAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val allowSelectQuerySources: Boolean = false,
    private val parameterBindingScope: ParameterBindingScope? = null,
) : IQueryTableApi {

    private var configuredAlias: String? = null
    private var configuredModel: KClass<*>? = null
    private var derivedSelectConfigured = false

    internal var selectedCteSource: Cte? = null
        private set

    internal val selectedOccurrenceAlias: String?
        get() = configuredAlias

    constructor(
        ast: IQueryTableAst = QueryTableAst(),
    ) : this(ast, ModelTableAliasContext())

    internal constructor(
        ast: IQueryTableAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(ast, ModelTableAliasContext(), parameterBindingScope = parameterBindingScope)

    override fun <T : IModelBase> table(
        table: KClass<T>,
    ): IQueryTableApi = model(table)

    override fun <T : IModelBase> model(
        model: KClass<T>,
    ): IQueryTableApi {
        check(selectedCteSource == null && ast.derivedQuery == null && !derivedSelectConfigured) {
            "A model source cannot be combined with a Query or nested SELECT source"
        }
        ModelMetadataResolver.requireTable(model)
        val effectiveAlias = configuredAlias ?: ModelMetadataResolver.tableAlias(model)

        configuredModel?.takeIf { it != model }?.let { modelAliasContext.unregisterTable(it) }
        modelAliasContext.unregisterNamedSource(ast)
        val metadata = ast.metadata
        metadata.cteRuntimeName = null
        metadata.logicalQueryName = null
        metadata.isCteObjectSource = false
        metadata.nameFromModel = ModelMetadataResolver.tableNameFromModel(model)
        metadata.nameFromAnnotation = ModelMetadataResolver.tableNameFromAnnotation(model)
        metadata.nameFromAst = null
        metadata.aliasFromModel = ModelMetadataResolver.tableAliasFromModel(model)
        metadata.aliasFromAnnotation = ModelMetadataResolver.tableAliasFromAnnotation(model)
        metadata.aliasFromAst = configuredAlias

        configuredModel = model
        modelAliasContext.registerTable(
            model,
            metadata.aliasFromModel,
            metadata.aliasFromAnnotation,
            metadata.aliasFromAst,
        )
        modelAliasContext.registerNamedSource(
            ast,
            ModelMetadataResolver.tableName(model),
            effectiveAlias,
            metadata.aliasFromModel,
            metadata.aliasFromAnnotation,
            metadata.aliasFromAst,
        )
        return this;
    }

    override fun table(
        table: String,
    ): IQueryTableApi {
        check(selectedCteSource == null && ast.derivedQuery == null && !derivedSelectConfigured) {
            "A table source cannot be combined with a Query or nested SELECT source"
        }
        modelAliasContext.unregisterNamedSource(ast)
        configuredModel?.let { modelAliasContext.unregisterTable(it) }
        configuredModel = null
        ast.metadata.cteRuntimeName = null
        ast.metadata.logicalQueryName = null
        ast.metadata.isCteObjectSource = false
        ast.metadata.nameFromModel = null
        ast.metadata.nameFromAnnotation = null
        ast.metadata.nameFromAst = table
        ast.metadata.aliasFromModel = null
        ast.metadata.aliasFromAnnotation = null
        ast.metadata.aliasFromAst = configuredAlias
        modelAliasContext.registerNamedSource(
            ast,
            table,
            configuredAlias ?: table,
            aliasFromAst = configuredAlias,
        )
        return this;
    }

    override fun table(
        blockQuery: IQueryRenderSelectApi.() -> Unit,
    ): IQueryTableApi {
        check(allowSelectQuerySources) {
            "A nested SELECT source is only available in a SELECT FROM block"
        }
        check(selectedCteSource == null && ast.metadata.resolveName() == null && !derivedSelectConfigured) {
            "Only one source can be configured in a SELECT FROM block"
        }

        val nestedAst = QueryRenderSelectAst()
        val nestedBuilder = parameterBindingScope?.let {
            QueryRenderSelectBuilder( nestedAst, it)
        } ?: QueryRenderSelectBuilder( nestedAst)
        nestedBuilder.apply(blockQuery)
        modelAliasContext.unregisterNamedSource(ast)
        ast.derivedQuery = nestedAst
        ast.metadata.nameFromModel = null
        ast.metadata.nameFromAnnotation = null
        ast.metadata.nameFromAst = null
        ast.metadata.aliasFromModel = null
        ast.metadata.aliasFromAnnotation = null
        ast.metadata.aliasFromAst = configuredAlias
        configuredModel = null
        derivedSelectConfigured = true
        ast.metadata.cteRuntimeName = null
        ast.metadata.logicalQueryName = null
        ast.metadata.isCteObjectSource = false
        configuredAlias?.let {
            modelAliasContext.registerNamedSource(ast, it, it, aliasFromAst = it)
        }
        return this
    }

    override fun cte(cte: Cte): IQueryTableApi {
        check(allowSelectQuerySources) {
            "A Cte object source is only available in a SELECT FROM block"
        }
        check(selectedCteSource == null && ast.metadata.resolveName() == null && !derivedSelectConfigured) {
            "Only one source can be configured in a SELECT FROM block"
        }
        modelAliasContext.unregisterNamedSource(ast)
        configuredModel?.let { modelAliasContext.unregisterTable(it) }
        configuredModel = null
        selectedCteSource = cte
        return this
    }

    override fun cte(
        cte: String,
    ): IQueryTableApi {
        check(selectedCteSource == null && ast.derivedQuery == null && !derivedSelectConfigured) {
            "A CTE source cannot be combined with a Query or nested SELECT source"
        }
        modelAliasContext.unregisterNamedSource(ast)
        configuredModel?.let { modelAliasContext.unregisterTable(it) }
        configuredModel = null
        ast.metadata.cteRuntimeName = cte
        ast.metadata.logicalQueryName = null
        ast.metadata.isCteObjectSource = false
        ast.metadata.nameFromModel = null
        ast.metadata.nameFromAnnotation = null
        ast.metadata.nameFromAst = cte
        ast.metadata.aliasFromModel = null
        ast.metadata.aliasFromAnnotation = null
        ast.metadata.aliasFromAst = configuredAlias
        modelAliasContext.registerNamedSource(
            ast,
            cte,
            configuredAlias ?: cte,
            aliasFromAst = configuredAlias,
        )
        return this;
    }

    override fun alias(alias: String): IQueryTableApi {
        configuredAlias = alias
        ast.metadata.aliasFromAst = alias
        configuredModel?.let { model ->
            modelAliasContext.registerTable(
                model,
                ast.metadata.aliasFromModel,
                ast.metadata.aliasFromAnnotation,
                ast.metadata.aliasFromAst,
            )
        }
        val sourceName = ast.metadata.resolveName()
        if (sourceName != null) {
            modelAliasContext.registerNamedSource(
                ast,
                sourceName,
                alias,
                ast.metadata.aliasFromModel,
                ast.metadata.aliasFromAnnotation,
                ast.metadata.aliasFromAst,
            )
        } else if (derivedSelectConfigured) {
            modelAliasContext.registerNamedSource(ast, alias, alias, aliasFromAst = alias)
        }
        return this
    }

}
