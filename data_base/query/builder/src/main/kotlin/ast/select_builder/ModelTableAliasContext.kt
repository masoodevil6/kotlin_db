package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.ast.interfaces.select_interface.column_base.IQueryColumnsBaseAst
import gog.my_project.data_base.query.ast.metadata.select.column_base.QueryColumnsBaseAstMetadata
import gog.my_project.data_base.query.builder.metadata.models.ModelMetadataResolver
import kotlin.reflect.KClass

/** Keeps typed SELECT qualifiers aligned with the effective table alias for one query. */
internal class ModelTableAliasContext {

    private val aliasesFromModel = mutableMapOf<KClass<*>, String>()
    private val aliasesFromAnnotation = mutableMapOf<KClass<*>, String>()
    private val aliasesFromAst = mutableMapOf<KClass<*>, String>()
    private val typedColumns = mutableMapOf<KClass<*>, MutableList<IQueryColumnsBaseAst>>()
    private data class NamedSource(
        val token: Any,
        val name: String,
        val alias: String,
        val aliasFromModel: String?,
        val aliasFromAnnotation: String?,
        val aliasFromAst: String?,
    )

    private val namedSources = mutableListOf<NamedSource>()
    private val selectedSqlSources = java.util.IdentityHashMap<IQueryColumnsBaseAst, String>()

    fun effectiveAlias(model: KClass<*>): String =
        aliasesFromAst[model]
            ?: aliasesFromAnnotation[model]
            ?: aliasesFromModel[model]
            ?: ModelMetadataResolver.tableAlias(model)

    fun registerTable(
        model: KClass<*>,
        aliasFromModel: String?,
        aliasFromAnnotation: String?,
        aliasFromAst: String?,
    ) {
        set(aliasesFromModel, model, aliasFromModel)
        set(aliasesFromAnnotation, model, aliasFromAnnotation)
        set(aliasesFromAst, model, aliasFromAst)
        typedColumns[model]?.forEach { applyModelAlias(model, it.metadata) }
    }

    fun unregisterTable(model: KClass<*>) {
        aliasesFromModel.remove(model)
        aliasesFromAnnotation.remove(model)
        aliasesFromAst.remove(model)
        typedColumns[model]?.forEach { applyModelAlias(model, it.metadata) }
    }

    fun registerColumn(model: KClass<*>, column: IQueryColumnsBaseAst) {
        val columns = typedColumns.getOrPut(model) { mutableListOf() }
        if (columns.none { it === column }) columns += column
        applyModelAlias(model, column.metadata)
    }

    fun applyModelAlias(model: KClass<*>, metadata: QueryColumnsBaseAstMetadata) {
        metadata.tableAliasFromModel = aliasesFromModel[model] ?: ModelMetadataResolver.tableAliasFromModel(model)
        metadata.tableAliasFromAnnotation = aliasesFromAnnotation[model] ?: ModelMetadataResolver.tableAliasFromAnnotation(model)
        metadata.tableAliasFromAst = aliasesFromAst[model]
    }

    fun unregisterColumn(column: IQueryColumnsBaseAst) {
        typedColumns.values.forEach { columns -> columns.removeAll { it === column } }
        selectedSqlSources.remove(column)
    }

    fun registerNamedSource(
        token: Any,
        name: String,
        alias: String,
        aliasFromModel: String? = null,
        aliasFromAnnotation: String? = null,
        aliasFromAst: String? = null,
    ) {
        namedSources.removeAll { it.token === token }
        namedSources += NamedSource(token, name, alias, aliasFromModel, aliasFromAnnotation, aliasFromAst)
        refreshSelectedSqlSources()
    }

    fun unregisterNamedSource(token: Any) {
        if (namedSources.removeAll { it.token === token }) refreshSelectedSqlSources()
    }

    fun registerSqlSourceColumn(sourceName: String, column: IQueryColumnsBaseAst) {
        selectedSqlSources[column] = sourceName
        applySqlSourceAlias(sourceName, column.metadata)
    }

    fun unregisterSqlSourceColumn(column: IQueryColumnsBaseAst) {
        selectedSqlSources.remove(column)
    }

    private fun refreshSelectedSqlSources() {
        selectedSqlSources.forEach { (column, sourceName) ->
            applySqlSourceAlias(sourceName, column.metadata)
        }
    }

    private fun applySqlSourceAlias(sourceName: String, metadata: QueryColumnsBaseAstMetadata) {
        val source = resolveSource(sourceName)
        if (source == null) {
            metadata.tableAliasFromModel = null
            metadata.tableAliasFromAnnotation = null
            metadata.tableAliasFromAst = sourceName
            return
        }

        metadata.tableAliasFromModel = source.aliasFromModel
        metadata.tableAliasFromAnnotation = source.aliasFromAnnotation
        metadata.tableAliasFromAst = source.aliasFromAst
            ?: source.alias.takeIf { source.aliasFromModel == null && source.aliasFromAnnotation == null }
    }

    private fun resolveSource(sourceName: String): NamedSource? {
        namedSources.firstOrNull { it.alias == sourceName }?.let { return it }

        val matches = namedSources.filter { it.name == sourceName }
        val matchingAliases = matches.map { it.alias }.distinct()
        require(matchingAliases.size <= 1) {
            "Source $sourceName is ambiguous; select its SQL occurrence alias instead"
        }
        return matches.firstOrNull()
    }

    private fun set(target: MutableMap<KClass<*>, String>, model: KClass<*>, value: String?) {
        if (value == null) target.remove(model) else target[model] = value
    }
}
