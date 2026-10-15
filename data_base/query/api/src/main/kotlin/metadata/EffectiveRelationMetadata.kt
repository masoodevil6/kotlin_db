package gog.my_project.data_base.query.api.metadata

public data class EffectiveRelationMetadata(
    val logicalQueryName: String?,
    val cteRuntimeName: String?,
    val withSqlName: String?,
    val occurrenceAlias: String?,
    val outputs: List<EffectiveOutputMetadata>,
)
