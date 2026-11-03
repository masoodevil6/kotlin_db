package gog.my_project.data_base_schema.ast.schema.rename_column

import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst
import gog.my_project.data_base_schema.ast.metadata.rename_column.SchemaRenameColumnAstMetadata

class SchemaRenameColumnAst(
    override var metadata: SchemaRenameColumnAstMetadata = SchemaRenameColumnAstMetadata()
) : ISchemaRenameColumnAst {

}
