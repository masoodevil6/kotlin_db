package gog.my_project.data_base_schema.ast.schema.drop_table

import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst
import gog.my_project.data_base_schema.ast.metadata.drop_table.SchemaDropTableAstMetadata

class SchemaDropTableAst(
    override var metadata: SchemaDropTableAstMetadata = SchemaDropTableAstMetadata()
) : ISchemaDropTableAst {

}
