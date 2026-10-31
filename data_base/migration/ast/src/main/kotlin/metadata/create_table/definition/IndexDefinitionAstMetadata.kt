package gog.my_project.data_base.migration.ast.metadata.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod

data class IndexDefinitionAstMetadata(
    val indexName:      String,
    val columnNames:    List<String>,
    val indexMethod:    IndexMethod? = null,
) : IMigrationCreateTableDefinitionAstMetadata
