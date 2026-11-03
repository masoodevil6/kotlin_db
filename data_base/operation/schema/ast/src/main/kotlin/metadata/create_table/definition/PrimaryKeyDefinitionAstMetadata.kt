package gog.my_project.data_base_schema.ast.metadata.create_table.definition

data class PrimaryKeyDefinitionAstMetadata(
    val columnNames:     List<String>,
) : ISchemaCreateTableDefinitionAstMetadata
