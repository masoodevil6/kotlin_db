package gog.my_project.data_base_schema.ast.metadata.add_column

import gog.my_project.data_base_schema.ast.metadata.create_table.column.SchemaColumnAstMetadata
import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaAddColumnAstMetadata : ISchemaAstMetadata {
    var tableName: String? = null
}
