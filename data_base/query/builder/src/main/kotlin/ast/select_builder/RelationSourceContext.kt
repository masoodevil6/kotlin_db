package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.relations.BuiltQueryRelation
import kotlin.reflect.KClass

/** Tracks the single relation source and effective SQL alias for one SELECT query. */
internal class RelationSourceContext {
    private var relation: QueryRelation? = null
    private var effectiveAlias: String? = null
    private val dependencies = mutableListOf<QueryRelation>()

    fun register(
        relation: QueryRelation,
        alias: String,
    ) {
        require(this.relation == null) { "Only one QueryRelation source is supported in this version" }
        this.relation = relation
        this.effectiveAlias = alias
        dependencies.add(relation)
    }

    fun dependencies(): List<QueryRelation> = dependencies.toList()

    fun resolve(
        requested: QueryRelation,
        sqlOutputName: String,
    ): Pair<String, String> {
        val activeRelation = relation
            ?: throw IllegalArgumentException("QueryRelation must be added with from(relation) before referencing its output")
        require(activeRelation === requested) {
            "SQL output $sqlOutputName belongs to a QueryRelation that is not the active FROM source"
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
        sqlOutputName: String,
        canonicalRelationName: String?,
    ): String {
        val activeRelation = relation
            ?: throw IllegalArgumentException("QueryRelation must be added with from(relation) before referencing its output")
        val declarationOwner = (activeRelation as? BuiltQueryRelation)?.declarationOwner
        if (declarationOwner != null) {
            require(ownerType == declarationOwner) {
                "Typed output owner ${ownerType.qualifiedName} does not match active relation declaration ${declarationOwner.qualifiedName}"
            }
        } else {
            require(canonicalRelationName != null && activeRelation.name == canonicalRelationName) {
                "Typed output for relation ${canonicalRelationName ?: ownerType.qualifiedName} cannot be used with active relation ${activeRelation.name}"
            }
        }
        require(sqlOutputName.isNotBlank()) { "Relation SQL output name must not be blank" }
        require(sqlOutputName in activeRelation.definition.ast.selectAliases()) {
            "SQL output $sqlOutputName is not selected by relation ${activeRelation.name}"
        }
        return activeRelation.name
    }
}

internal fun IQueryRenderSelectAst.selectAliases(): List<String> =
    select?.columns.orEmpty().mapNotNull { it.ColumnAlias?.takeIf { name -> name.isNotBlank() } }
