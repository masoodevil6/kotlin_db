package gog.my_project.data_base.query.api.metadata

import gog.my_project.data_base.query.ast.enums.DataType

public data class EffectiveOutputMetadata(
    val logicalOutputName: String?,
    val publishedSqlAlias: String?,
    val executionType: DataType?,
    val tableName: String? = null,
    val tableAlias: String? = null,
)
