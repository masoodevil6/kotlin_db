package gog.my_project.data_base_schema.ast.metadata.create_foreign_key

import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

class SchemaCreateForeignKeyAstMetadata : ISchemaAstMetadata {
    var tableName:             String? = null
    var constraintName:        String? = null
    var columnNames:           List<String>? = null
    var referencesTableName:   String? = null
    var referencesColumnNames: List<String>? = null
    var onDelete:              ForeignKeyAction? = null
    var onUpdate:              ForeignKeyAction? = null
}
