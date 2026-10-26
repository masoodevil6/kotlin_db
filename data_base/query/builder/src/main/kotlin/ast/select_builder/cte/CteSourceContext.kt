package gog.my_project.data_base.query.builder.ast.select_builder.cte

import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.metadata.select.column_base.QueryColumnsBaseAstMetadata
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Tracks the single relation source and effective SQL alias for one SELECT query. */
internal class CteSourceContext {
    private var relation: Cte? = null
    private var effectiveAlias: String? = null
    private var aliasFromModel: String? = null
    private var aliasFromAnnotation: String? = null
    private var aliasFromAst: String? = null
    private var namedSourceBinding = false
    private var namedSourceError: String? = null
    private val dependencies = mutableListOf<Cte>()

    internal data class Snapshot(
        val relation: Cte,
        val effectiveAlias: String,
        val aliasFromModel: String?,
        val aliasFromAnnotation: String?,
        val aliasFromAst: String?,
    )

    fun register(
        relation: Cte,
        alias: String,
        aliasFromModel: String?,
        aliasFromAnnotation: String?,
        aliasFromAst: String?,
    ) {
        registerSource(relation, alias, aliasFromModel, aliasFromAnnotation, aliasFromAst)
        dependencies.add(relation)
    }

    fun registerInline(
        relation: Cte,
        alias: String,
        aliasFromModel: String?,
        aliasFromAnnotation: String?,
        aliasFromAst: String?,
    ) {
        registerSource(relation, alias, aliasFromModel, aliasFromAnnotation, aliasFromAst)
    }

    /** Binds a raw `cte(name)` occurrence to a unique relation already present in WITH. */
    fun registerNamed(
        relation: Cte,
        alias: String,
        aliasFromModel: String?,
        aliasFromAnnotation: String?,
        aliasFromAst: String?,
    ) {
        clearNamedBinding()
        require(this.relation == null) { "Only one Cte source is supported in this version" }
        registerSource(relation, alias, aliasFromModel, aliasFromAnnotation, aliasFromAst)
        namedSourceBinding = true
    }

    fun clearNamedBinding() {
        if (!namedSourceBinding) return
        relation = null
        effectiveAlias = null
        aliasFromModel = null
        aliasFromAnnotation = null
        aliasFromAst = null
        namedSourceBinding = false
        namedSourceError = null
    }

    fun markNamedSourceAmbiguous(message: String) {
        clearNamedBinding()
        require(relation == null) { "Only one Cte source is supported in this version" }
        namedSourceBinding = true
        namedSourceError = message
    }

    private fun registerSource(
        relation: Cte,
        alias: String,
        aliasFromModel: String?,
        aliasFromAnnotation: String?,
        aliasFromAst: String?,
    ) {
        require(this.relation == null) { "Only one Cte source is supported in this version" }
        this.relation = relation
        this.effectiveAlias = alias
        this.aliasFromModel = aliasFromModel
        this.aliasFromAnnotation = aliasFromAnnotation
        this.aliasFromAst = aliasFromAst
    }

    fun dependencies(): List<Cte> = dependencies.toList()

    fun snapshot(): Snapshot? = relation?.let {
        Snapshot(
            it,
            effectiveAlias ?: it.name,
            aliasFromModel,
            aliasFromAnnotation,
            aliasFromAst,
        )
    }

    fun applyOccurrenceAlias(metadata: QueryColumnsBaseAstMetadata) {
        val active = snapshot()
            ?: throw IllegalArgumentException("A Query source must be added in the SELECT FROM block before referencing its output")
        metadata.tableAliasFromModel = active.aliasFromModel
        metadata.tableAliasFromAnnotation = active.aliasFromAnnotation
        metadata.tableAliasFromAst = active.aliasFromAst
    }

    fun resolve(
        requested: Cte,
        sqlOutputName: String,
    ): Pair<String, String> {
        val activeRelation = requireRelation()
        require(activeRelation === requested) {
            "SQL output $sqlOutputName belongs to a Cte that is not the active FROM source"
        }
        require(sqlOutputName.isNotBlank()) { "Relation SQL output name must not be blank" }
        val publishedNames = requested.definition.ast.selectAliases()
        require(sqlOutputName in publishedNames) {
            "SQL output $sqlOutputName is not selected by relation ${requested.name}"
        }
        return (effectiveAlias ?: requested.name) to sqlOutputName
    }

    fun validateTypedSource(
        ownerType: KClass<*>,
        canonicalRelationName: String?,
    ): String {
        val activeRelation = requireRelation()
        val active = snapshot()!!
        val declarationOwner = (activeRelation as? BuiltCteComposition)?.declarationOwner
        if (declarationOwner != null) {
            require(ownerType == declarationOwner) {
                "Typed output owner ${ownerType.qualifiedName} does not match active relation declaration ${declarationOwner.qualifiedName}"
            }
        } else {
            require(canonicalRelationName != null && activeRelation.name == canonicalRelationName) {
                "Typed output for relation ${canonicalRelationName ?: ownerType.qualifiedName} cannot be used with active relation ${activeRelation.name}"
            }
        }
        return active.effectiveAlias
    }

    fun validateTypedOutput(
        ownerType: KClass<*>,
        property: KProperty1<*, *>,
        canonicalRelationName: String?,
    ): Pair<String, String> {
        val effectiveAlias = validateTypedSource(ownerType, canonicalRelationName)
        val activeRelation = requireRelation()
        val matchingOutputs = activeRelation.definition.ast.select?.columns.orEmpty()
            .filter { it.metadata.PropertyReference == property }
        require(matchingOutputs.size <= 1) {
            "Typed output ${property.name} is selected more than once; use an explicit SQL output name"
        }
        val sqlOutputName = matchingOutputs.singleOrNull()?.metadata?.ColumnAlias ?: property.name
        require(sqlOutputName.isNotBlank()) { "Relation SQL output name must not be blank" }
        require(sqlOutputName in activeRelation.definition.ast.selectAliases()) {
            "SQL output $sqlOutputName is not selected by relation ${activeRelation.name}"
        }
        return effectiveAlias to sqlOutputName
    }

    private fun requireRelation(): Cte = relation
        ?: throw IllegalArgumentException(
            namedSourceError
                ?: "A Query source must be added in the SELECT FROM block before referencing its output",
        )
}

internal fun IQueryRenderSelectAst.selectAliases(): List<String> =
    select?.columns.orEmpty().mapNotNull { it.metadata.ColumnAlias?.takeIf { name -> name.isNotBlank() } }
