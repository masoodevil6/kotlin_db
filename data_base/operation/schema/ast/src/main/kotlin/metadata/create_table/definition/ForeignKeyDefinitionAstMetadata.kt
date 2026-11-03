package gog.my_project.data_base_schema.ast.metadata.create_table.definition

import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction

data class ForeignKeyDefinitionAstMetadata(
    val constraintName:           String,
    val columnNames:              List<String>,
    val referencesTableName:      String,
    val referencesColumnNames:    List<String>,
    val onDelete:                 ForeignKeyAction? = null,
    val onUpdate:                 ForeignKeyAction? = null,
) : ISchemaCreateTableDefinitionAstMetadata
