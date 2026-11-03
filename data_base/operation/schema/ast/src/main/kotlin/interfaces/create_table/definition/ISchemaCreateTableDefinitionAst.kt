package gog.my_project.data_base_schema.ast.interfaces.create_table.definition

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.ast.metadata.create_table.definition.ISchemaCreateTableDefinitionAstMetadata

sealed interface ISchemaCreateTableDefinitionAst<MD : ISchemaCreateTableDefinitionAstMetadata> : ISchemaAst<MD>
