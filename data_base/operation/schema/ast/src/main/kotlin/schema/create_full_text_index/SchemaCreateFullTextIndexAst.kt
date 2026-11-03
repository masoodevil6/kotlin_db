package gog.my_project.data_base_schema.ast.schema.create_full_text_index

import gog.my_project.data_base_schema.ast.interfaces.create_full_text_index.ISchemaCreateFullTextIndexAst
import gog.my_project.data_base_schema.ast.metadata.create_full_text_index.SchemaCreateFullTextIndexAstMetadata

class SchemaCreateFullTextIndexAst(
    override var metadata: SchemaCreateFullTextIndexAstMetadata = SchemaCreateFullTextIndexAstMetadata()
) : ISchemaCreateFullTextIndexAst {

}
