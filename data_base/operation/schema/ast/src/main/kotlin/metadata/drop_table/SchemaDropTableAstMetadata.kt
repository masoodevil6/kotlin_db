package gog.my_project.data_base_schema.ast.metadata.drop_table

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaDropTableAstMetadata : ISchemaAstMetadata {
    var tableName: String? = null
    var ifExists: Boolean = false
}
