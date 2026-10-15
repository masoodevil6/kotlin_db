package gog.my_project.data_base.query.api.metadata

public data class EffectiveQueryMetadata(
    val relations: List<EffectiveRelationMetadata>,
    /** Outputs of this query's final SELECT projection; relation declaration outputs stay under [relations]. */
    val outputs: List<EffectiveOutputMetadata> = emptyList(),
)
