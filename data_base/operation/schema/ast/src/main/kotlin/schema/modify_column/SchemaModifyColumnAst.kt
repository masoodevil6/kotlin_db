package gog.my_project.data_base_schema.ast.schema.modify_column

import gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst
import gog.my_project.data_base_schema.ast.metadata.modify_column.SchemaModifyColumnAstMetadata

class SchemaModifyColumnAst(
    override var metadata: SchemaModifyColumnAstMetadata = SchemaModifyColumnAstMetadata()
) : ISchemaModifyColumnAst {

}
