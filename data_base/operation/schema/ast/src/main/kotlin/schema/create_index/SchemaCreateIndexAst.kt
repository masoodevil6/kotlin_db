package gog.my_project.data_base_schema.ast.schema.create_index

import gog.my_project.data_base_schema.ast.interfaces.create_index.ISchemaCreateIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base_schema.ast.metadata.create_index.SchemaCreateIndexAstMetadata

class SchemaCreateIndexAst(
    override var metadata: SchemaCreateIndexAstMetadata = SchemaCreateIndexAstMetadata()
) : ISchemaCreateIndexAst {

}
