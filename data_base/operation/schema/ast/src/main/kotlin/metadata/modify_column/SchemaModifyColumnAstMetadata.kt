package gog.my_project.data_base_schema.ast.metadata.modify_column

import gog.my_project.data_base_schema.ast.interfaces.modify_column.ColumnNullability
import gog.my_project.data_base_schema.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

class SchemaModifyColumnAstMetadata : ISchemaAstMetadata {
    var tableName:           String? = null
    var columnName:          String? = null
    var columnDataType:      SchemaColumnDataType? = null
    var nullability:         ColumnNullability? = null
    var defaultDefinition:   DefaultDefinition? = null
    var autoIncrement:       Boolean = false
}
