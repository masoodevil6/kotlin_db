package gog.my_project.data_base_schema.ast.metadata.drop_foreign_key

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaDropForeignKeyAstMetadata : ISchemaAstMetadata {
    var tableName:      String? = null
    var constraintName: String? = null
}
