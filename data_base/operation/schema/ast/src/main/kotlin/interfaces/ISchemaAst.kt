package gog.my_project.data_base_schema.ast.interfaces

import gog.my_project.data_base_schema.ast.metadata.interfaces.ISchemaAstMetadata

interface ISchemaAst<MD : ISchemaAstMetadata> {
    var metadata: MD
}