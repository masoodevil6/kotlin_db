package gog.my_project.data_base.migration.ast.metadata.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

data class ForeignKeyDefinitionAstMetadata(
    val constraintName:           String,
    val columnNames:              List<String>,
    val referencesTableName:      String,
    val referencesColumnNames:    List<String>,
    val onDelete:                 ForeignKeyAction? = null,
    val onUpdate:                 ForeignKeyAction? = null,
) : IMigrationCreateTableDefinitionAstMetadata
