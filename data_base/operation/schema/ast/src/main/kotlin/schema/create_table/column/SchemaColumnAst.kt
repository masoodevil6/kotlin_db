package gog.my_project.data_base_schema.ast.schema.create_table.column

import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.metadata.create_table.column.SchemaColumnAstMetadata
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

class SchemaColumnAst(
    override var metadata: SchemaColumnAstMetadata = SchemaColumnAstMetadata()
) : ISchemaColumnAst {


}
