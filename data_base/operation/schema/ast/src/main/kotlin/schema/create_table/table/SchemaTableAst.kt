package gog.my_project.data_base_schema.ast.schema.create_table.table

import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.ast.metadata.create_table.table.SchemaTableAstMetadata

class SchemaTableAst(
    override var metadata: SchemaTableAstMetadata = SchemaTableAstMetadata()
) : ISchemaTableAst {

}
