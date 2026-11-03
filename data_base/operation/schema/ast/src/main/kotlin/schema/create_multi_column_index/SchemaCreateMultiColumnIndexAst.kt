package gog.my_project.data_base_schema.ast.schema.create_multi_column_index

import gog.my_project.data_base_schema.ast.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexAst
import gog.my_project.data_base_schema.ast.metadata.create_multi_column_index.SchemaCreateMultiColumnIndexAstMetadata

class SchemaCreateMultiColumnIndexAst(
    override var metadata: SchemaCreateMultiColumnIndexAstMetadata = SchemaCreateMultiColumnIndexAstMetadata()
) : ISchemaCreateMultiColumnIndexAst {

}
