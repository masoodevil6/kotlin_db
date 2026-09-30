package gog.my_project.data_base.migration.ast.interfaces.modify_column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

interface IMigrationModifyColumnAst : IMigrationAst {
    val tableName: String
    val columnName: String
    val columnDataType: MigrationColumnDataType
    val nullability: ColumnNullability
    val defaultDefinition: DefaultDefinition
    val autoIncrement: Boolean
}

enum class ColumnNullability {
    NULL,
    NOT_NULL,
}

sealed interface DefaultDefinition {
    data object NoDefaultClause : DefaultDefinition
    data object DefaultNull : DefaultDefinition
    data class DefaultValue(val value: Any) : DefaultDefinition
}
