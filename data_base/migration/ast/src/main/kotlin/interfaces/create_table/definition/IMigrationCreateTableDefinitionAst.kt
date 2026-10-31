package gog.my_project.data_base.migration.ast.interfaces.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.metadata.create_table.definition.IMigrationCreateTableDefinitionAstMetadata

sealed interface IMigrationCreateTableDefinitionAst<MD : IMigrationCreateTableDefinitionAstMetadata> : IMigrationAst<MD>
