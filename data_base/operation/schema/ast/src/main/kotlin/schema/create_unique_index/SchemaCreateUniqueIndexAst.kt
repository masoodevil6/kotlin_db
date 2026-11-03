package gog.my_project.data_base_schema.ast.schema.create_unique_index

import gog.my_project.data_base_schema.ast.interfaces.create_unique_index.ISchemaCreateUniqueIndexAst
import gog.my_project.data_base_schema.ast.metadata.create_unique_index.SchemaCreateUniqueIndexAstMetadata

class SchemaCreateUniqueIndexAst(
    override var metadata: SchemaCreateUniqueIndexAstMetadata = SchemaCreateUniqueIndexAstMetadata()
) : ISchemaCreateUniqueIndexAst {

}
