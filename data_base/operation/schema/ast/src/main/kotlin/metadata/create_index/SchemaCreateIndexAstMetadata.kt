package gog.my_project.data_base_schema.ast.metadata.create_index

import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaCreateIndexAstMetadata : ISchemaAstMetadata {
    var tableName:      String? = null
    var indexName:      String? = null
    var columnName:     String? = null
    var indexMethod:    IndexMethod? = null
}
