package gog.my_project.data_base_schema.ast.metadata.rename_table

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaRenameTableAstMetadata : ISchemaAstMetadata {
    var fromTableName:  String? = null
    var toTableName:    String? = null
}
