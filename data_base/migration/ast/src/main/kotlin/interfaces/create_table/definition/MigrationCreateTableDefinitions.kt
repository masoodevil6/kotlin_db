package gog.my_project.data_base.migration.ast.interfaces.create_table.definition

import gog.my_project.data_base.migration.ast.metadata.create_table.definition.ForeignKeyDefinitionAstMetadata
import gog.my_project.data_base.migration.ast.metadata.create_table.definition.FullTextIndexDefinitionAstMetadata
import gog.my_project.data_base.migration.ast.metadata.create_table.definition.IndexDefinitionAstMetadata
import gog.my_project.data_base.migration.ast.metadata.create_table.definition.PrimaryKeyDefinitionAstMetadata
import gog.my_project.data_base.migration.ast.metadata.create_table.definition.UniqueDefinitionAstMetadata

data class PrimaryKeyDefinitionAst(
    override var metadata: PrimaryKeyDefinitionAstMetadata,
) : IMigrationCreateTableDefinitionAst<PrimaryKeyDefinitionAstMetadata>

data class UniqueDefinitionAst(
    override var metadata: UniqueDefinitionAstMetadata,
) : IMigrationCreateTableDefinitionAst<UniqueDefinitionAstMetadata>

data class IndexDefinitionAst(
    override var metadata: IndexDefinitionAstMetadata,
) : IMigrationCreateTableDefinitionAst<IndexDefinitionAstMetadata>

data class FullTextIndexDefinitionAst(
    override var metadata: FullTextIndexDefinitionAstMetadata,
) : IMigrationCreateTableDefinitionAst<FullTextIndexDefinitionAstMetadata>

data class ForeignKeyDefinitionAst(
    override var metadata: ForeignKeyDefinitionAstMetadata,
) : IMigrationCreateTableDefinitionAst<ForeignKeyDefinitionAstMetadata>
