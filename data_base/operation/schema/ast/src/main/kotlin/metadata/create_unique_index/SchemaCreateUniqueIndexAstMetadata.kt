package gog.my_project.data_base_schema.ast.metadata.create_unique_index

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaCreateUniqueIndexAstMetadata : ISchemaAstMetadata {
    var tableName:    String? = null
    var indexName:    String? = null
    var columnName:   String? = null
}
