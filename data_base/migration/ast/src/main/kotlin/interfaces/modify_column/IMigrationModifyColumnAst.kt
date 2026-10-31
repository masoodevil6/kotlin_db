package gog.my_project.data_base.migration.ast.interfaces.modify_column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.metadata.modify_column.MigrationModifyColumnAstMetadata

interface IMigrationModifyColumnAst : IMigrationAst<MigrationModifyColumnAstMetadata>

enum class ColumnNullability {
    NULL,
    NOT_NULL,
}

sealed interface DefaultDefinition {
    data object NoDefaultClause : DefaultDefinition
    data object DefaultNull : DefaultDefinition
    data class DefaultValue(val value: Any) : DefaultDefinition
}
