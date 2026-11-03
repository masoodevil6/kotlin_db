package gog.my_project.data_base_schema.ast.interfaces.create_table.definition

import gog.my_project.data_base_schema.ast.metadata.create_table.definition.ForeignKeyDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.FullTextIndexDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.IndexDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.PrimaryKeyDefinitionAstMetadata
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.UniqueDefinitionAstMetadata

data class PrimaryKeyDefinitionAst(
    override var metadata: PrimaryKeyDefinitionAstMetadata,
) : ISchemaCreateTableDefinitionAst<PrimaryKeyDefinitionAstMetadata>

data class UniqueDefinitionAst(
    override var metadata: UniqueDefinitionAstMetadata,
) : ISchemaCreateTableDefinitionAst<UniqueDefinitionAstMetadata>

data class IndexDefinitionAst(
    override var metadata: IndexDefinitionAstMetadata,
) : ISchemaCreateTableDefinitionAst<IndexDefinitionAstMetadata>

data class FullTextIndexDefinitionAst(
    override var metadata: FullTextIndexDefinitionAstMetadata,
) : ISchemaCreateTableDefinitionAst<FullTextIndexDefinitionAstMetadata>

data class ForeignKeyDefinitionAst(
    override var metadata: ForeignKeyDefinitionAstMetadata,
) : ISchemaCreateTableDefinitionAst<ForeignKeyDefinitionAstMetadata>
