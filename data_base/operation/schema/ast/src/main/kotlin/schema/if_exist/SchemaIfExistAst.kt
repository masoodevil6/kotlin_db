package gog.my_project.data_base_schema.ast.schema.if_exist

import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.ast.metadata.if_exist.SchemaIfExistAstMetadata

class SchemaIfExistAst(
    override var metadata: SchemaIfExistAstMetadata = SchemaIfExistAstMetadata(),
) : ISchemaIfExistAst
