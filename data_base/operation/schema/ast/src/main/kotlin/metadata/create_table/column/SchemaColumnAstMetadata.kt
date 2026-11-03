package gog.my_project.data_base_schema.ast.metadata.create_table.column

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

class SchemaColumnAstMetadata : ISchemaAstMetadata {
    var columnName:          String? = null
    var columnDataType:      SchemaColumnDataType? = null

    var columnDefault:       Any? = null

    var hasColumnDefault:    Boolean = false
    var columnNullable:      Boolean = false
    var columnAutoIncrement: Boolean = false
    var columnPrimary:       Boolean = false

}
