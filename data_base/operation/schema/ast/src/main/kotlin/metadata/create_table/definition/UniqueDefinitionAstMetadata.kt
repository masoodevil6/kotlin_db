package gog.my_project.data_base_schema.ast.metadata.create_table.definition

data class UniqueDefinitionAstMetadata(
    val indexName:     String,
    val columnNames:   List<String>,
) : ISchemaCreateTableDefinitionAstMetadata
