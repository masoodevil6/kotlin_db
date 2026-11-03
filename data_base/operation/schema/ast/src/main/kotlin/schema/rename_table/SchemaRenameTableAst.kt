package gog.my_project.data_base_schema.ast.schema.rename_table

import gog.my_project.data_base_schema.ast.interfaces.rename_table.ISchemaRenameTableAst
import gog.my_project.data_base_schema.ast.metadata.rename_table.SchemaRenameTableAstMetadata

class SchemaRenameTableAst(
    override var metadata: SchemaRenameTableAstMetadata = SchemaRenameTableAstMetadata()
) : ISchemaRenameTableAst {

}
