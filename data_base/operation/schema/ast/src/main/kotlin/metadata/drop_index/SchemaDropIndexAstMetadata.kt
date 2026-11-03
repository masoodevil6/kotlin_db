package gog.my_project.data_base_schema.ast.metadata.drop_index

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaDropIndexAstMetadata : ISchemaAstMetadata {
    var tableName: String? = null
    var indexName: String? = null
}
