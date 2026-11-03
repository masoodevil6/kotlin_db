package gog.my_project.data_base_schema.ast.interfaces.modify_column

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.ast.metadata.modify_column.SchemaModifyColumnAstMetadata

interface ISchemaModifyColumnAst : ISchemaAst<SchemaModifyColumnAstMetadata>

enum class ColumnNullability {
    NULL,
    NOT_NULL,
}

sealed interface DefaultDefinition {
    data object NoDefaultClause : DefaultDefinition
    data object DefaultNull : DefaultDefinition
    data class DefaultValue(val value: Any) : DefaultDefinition
}
