package gog.my_project.data_base.query.builder.ast.select_builder.cte

import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/** Tracks the single relation source and effective SQL alias for one SELECT query. */
internal class CteSourceContext {
    private var relation: Cte? = null
    private var effectiveAlias: String? = null
    private val dependencies = mutableListOf<Cte>()

    internal data class Snapshot(
        val relation: Cte,
        val effectiveAlias: String,
    )

    fun register(
        relation: Cte,
        alias: String,
    ) {
        registerSource(relation, alias)
        dependencies.add(relation)
    }

    fun registerInline(
        relation: Cte,
        alias: String,
    ) {
        registerSource(relation, alias)
    }

    private fun registerSource(
        relation: Cte,
        alias: String,
    ) {
        require(this.relation == null) { "Only one Cte source is supported in this version" }
        this.relation = relation
        this.effectiveAlias = alias
    }

    fun dependencies(): List<Cte> = dependencies.toList()

    fun snapshot(): Snapshot? = relation?.let { Snapshot(it, effectiveAlias ?: it.name) }

    fun resolve(
        requested: Cte,
        sqlOutputName: String,
    ): Pair<String, String> {
        val activeRelation = relation
            ?: throw IllegalArgumentException("A Query source must be added in the SELECT FROM block before referencing its output")
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

    fun validateTypedOutput(
        ownerType: KClass<*>,
        property: KProperty1<*, *>,
        canonicalRelationName: String?,
    ): Pair<String, String> {
        val activeRelation = relation
            ?: throw IllegalArgumentException("A Query source must be added in the SELECT FROM block before referencing its output")
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
        val matchingOutputs = activeRelation.definition.ast.select?.columns.orEmpty()
            .filter { it.PropertyReference == property }
        require(matchingOutputs.size <= 1) {
            "Typed output ${property.name} is selected more than once; use an explicit SQL output name"
        }
        val sqlOutputName = matchingOutputs.singleOrNull()?.ColumnAlias ?: property.name
        require(sqlOutputName.isNotBlank()) { "Relation SQL output name must not be blank" }
        require(sqlOutputName in activeRelation.definition.ast.selectAliases()) {
            "SQL output $sqlOutputName is not selected by relation ${activeRelation.name}"
        }
        return (effectiveAlias ?: activeRelation.name) to sqlOutputName
    }
}

internal fun IQueryRenderSelectAst.selectAliases(): List<String> =
    select?.columns.orEmpty().mapNotNull { it.ColumnAlias?.takeIf { name -> name.isNotBlank() } }
