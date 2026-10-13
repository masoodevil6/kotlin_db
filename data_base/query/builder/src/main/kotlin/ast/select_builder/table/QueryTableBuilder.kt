package gog.my_project.data_base.query.builder.ast.select_builder.table

import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema.query_render_select.QueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import gog.my_project.data_base.query.builder.ast.select_builder.ParameterBindingScope
import gog.my_project.data_base.query.builder.ast.select_builder.metadata.QueryBuilderMetadata
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import kotlin.reflect.KClass

class QueryTableBuilder internal constructor(
    override var params: MutableList<SqlParameter<*>>,
    override var ast: IQueryTableAst,
    private val modelAliasContext: ModelTableAliasContext,
    private val allowSelectQuerySources: Boolean = false,
    private val parameterBindingScope: ParameterBindingScope? = null,
) : IQueryTableApi {

    private var configuredAlias: String? = null
    private var configuredModel: KClass<*>? = null
    private var derivedSelectConfigured = false

    internal var selectedQueryRelation: Cte? = null
        private set

    internal val selectedOccurrenceAlias: String?
        get() = configuredAlias

    constructor(
        params: MutableList<SqlParameter<*>> = mutableListOf(),
        ast: IQueryTableAst = QueryTableAst(),
    ) : this(params, ast, ModelTableAliasContext())

    internal constructor(
        params: MutableList<SqlParameter<*>>,
        ast: IQueryTableAst,
        parameterBindingScope: ParameterBindingScope,
    ) : this(params, ast, ModelTableAliasContext(), parameterBindingScope = parameterBindingScope)

    override fun <T : IModelBase> table(
        table: KClass<T>,
    ): IQueryTableApi = model(table)

    override fun <T : IModelBase> model(
        model: KClass<T>,
    ): IQueryTableApi {
        check(selectedQueryRelation == null && !derivedSelectConfigured) {
            "A model source cannot be combined with a Query or nested SELECT source"
        }
        val metadata = QueryBuilderMetadata.requireTable(model)

        val effectiveAlias = configuredAlias ?: QueryBuilderMetadata.tableAlias(model, metadata)
        configuredModel = model
        this.ast.table = QueryBuilderMetadata.tableName(model, metadata)
        // The current renderer uses the empty string as its no-alias sentinel.
        this.ast.tableAlias = effectiveAlias
        modelAliasContext.registerTable(model, effectiveAlias)
        return this;
    }

    override fun table(
        table: String,
    ): IQueryTableApi {
        check(selectedQueryRelation == null && !derivedSelectConfigured) {
            "A table source cannot be combined with a Query or nested SELECT source"
        }
        configuredModel = null
        this.ast.table = table
        this.ast.tableAlias = configuredAlias ?: ""
        return this;
    }

    override fun table(
        blockQuery: IQueryRenderSelectApi.() -> Unit,
    ): IQueryTableApi {
        check(allowSelectQuerySources) {
            "A nested SELECT source is only available in a SELECT FROM block"
        }
        check(selectedQueryRelation == null && ast.table == null && ast.cte == null && !derivedSelectConfigured) {
            "Only one source can be configured in a SELECT FROM block"
        }

        val nestedAst = QueryRenderSelectAst()
        val nestedParams = mutableListOf<SqlParameter<*>>()
        val nestedBuilder = parameterBindingScope?.let {
            QueryRenderSelectBuilder(nestedParams, nestedAst, it)
        } ?: QueryRenderSelectBuilder(nestedParams, nestedAst)
        nestedBuilder.apply(blockQuery)
        params.addAll(nestedParams)
        ast.table = null
        ast.cte = null
        ast.derivedQuery = nestedAst
        ast.tableAlias = configuredAlias ?: ""
        ast.cteAlias = null
        configuredModel = null
        derivedSelectConfigured = true
        return this
    }

    override fun <P> cte(
        declaration: ICteDeclaration<P>,
    ): IQueryTableApi {
        check(allowSelectQuerySources) {
            "A Query declaration source is only available in a SELECT FROM block"
        }
        check(selectedQueryRelation == null && ast.table == null && ast.cte == null && !derivedSelectConfigured) {
            "Only one source can be configured in a SELECT FROM block"
        }
        selectedQueryRelation = declaration.createCte(declaration.params)
        return this
    }

    override fun cte(
        cte: String,
    ): IQueryTableApi {
        check(selectedQueryRelation == null && !derivedSelectConfigured) {
            "A CTE source cannot be combined with a Query or nested SELECT source"
        }
        configuredModel = null
        this.ast.cte = cte
        this.ast.cteAlias = configuredAlias ?: ""
        return this;
    }

    override fun alias(alias: String): IQueryTableApi {
        configuredAlias = alias
        ast.tableAlias = alias
        ast.cteAlias = alias
        configuredModel?.let { modelAliasContext.registerTable(it, alias) }
        return this
    }

}
