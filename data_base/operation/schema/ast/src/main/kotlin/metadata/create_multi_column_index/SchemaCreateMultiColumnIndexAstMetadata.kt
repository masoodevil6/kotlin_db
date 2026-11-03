package gog.my_project.data_base_schema.ast.metadata.create_multi_column_index

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaCreateMultiColumnIndexAstMetadata : ISchemaAstMetadata {
    var tableName:     String? = null
    var indexName:     String? = null
    var columnNames:   List<String>? = null
}
