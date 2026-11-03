package gog.my_project.data_base_schema.ast.metadata.rename_column

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaRenameColumnAstMetadata : ISchemaAstMetadata {
    var tableName:   String? = null
    var name:        String? = null
    var to:          String? = null
}
