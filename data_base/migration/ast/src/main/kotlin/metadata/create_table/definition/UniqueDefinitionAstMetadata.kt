package gog.my_project.data_base.migration.ast.metadata.create_table.definition

data class UniqueDefinitionAstMetadata(
    val indexName:     String,
    val columnNames:   List<String>,
) : IMigrationCreateTableDefinitionAstMetadata
