package gog.my_project.data_base.query.builder.ast.select_builder.table

import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.table.IQueryTableApi
import gog.my_project.data_base.query.ast.interfaces.select_interface.table.IQueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.builder.ast.select_builder.ModelTableAliasContext
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

class QueryTableBuilder internal constructor(
    override var params: MutableList<SqlParameter<*>>,
    override var ast: IQueryTableAst,
    private val modelAliasContext: ModelTableAliasContext,
) : IQueryTableApi {

    private var configuredAlias: String? = null
    private var configuredModel: KClass<*>? = null

    constructor(
        params: MutableList<SqlParameter<*>> = mutableListOf(),
        ast: IQueryTableAst = QueryTableAst(),
    ) : this(params, ast, ModelTableAliasContext())

    override fun <T : IModelBase> table(
        table: KClass<T>,
    ): IQueryTableApi {
        val metadata = table.findAnnotation<QBTable>()
            ?: throw IllegalArgumentException("${table.qualifiedName} must be annotated with @QBTable")
        require(metadata.name.isNotEmpty()) {
            "@QBTable on ${table.qualifiedName} must declare a non-empty table name"
        }

        val effectiveAlias = configuredAlias ?: metadata.alias
        configuredModel = table
        this.ast.table = metadata.name
        // The current renderer uses the empty string as its no-alias sentinel.
        this.ast.tableAlias = effectiveAlias
        modelAliasContext.registerTable(table, effectiveAlias)
        return this;
    }

    override fun table(
        table: String,
    ): IQueryTableApi {
        configuredModel = null
        this.ast.table = table
        this.ast.tableAlias = configuredAlias ?: ""
        return this;
    }

    override fun cte(
        cte: String,
    ): IQueryTableApi {
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
