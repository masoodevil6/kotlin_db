package gog.my_project.data_base.migration.ast.interfaces.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

data class PrimaryKeyDefinitionAst(
    val columnNames: List<String>,
) : IMigrationCreateTableDefinitionAst

data class UniqueDefinitionAst(
    val indexName: String,
    val columnNames: List<String>,
) : IMigrationCreateTableDefinitionAst

data class IndexDefinitionAst(
    val indexName: String,
    val columnNames: List<String>,
    val indexMethod: IndexMethod? = null,
) : IMigrationCreateTableDefinitionAst

data class FullTextIndexDefinitionAst(
    val indexName: String,
    val columnNames: List<String>,
) : IMigrationCreateTableDefinitionAst

data class ForeignKeyDefinitionAst(
    val constraintName: String,
    val columnNames: List<String>,
    val referencesTableName: String,
    val referencesColumnNames: List<String>,
    val onDelete: ForeignKeyAction? = null,
    val onUpdate: ForeignKeyAction? = null,
) : IMigrationCreateTableDefinitionAst
