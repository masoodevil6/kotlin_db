package gog.my_project.data_base_schema.ast.metadata.drop_column

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaDropColumnAstMetadata : ISchemaAstMetadata {
    var tableName:   String? = null
    var name:        String? = null
}
