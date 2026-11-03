package gog.my_project.data_base_schema.ast.schema.add_column

import gog.my_project.data_base_schema.ast.interfaces.add_column.ISchemaAddColumnAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.metadata.add_column.SchemaAddColumnAstMetadata

class SchemaAddColumnAst(
    override var metadata: SchemaAddColumnAstMetadata = SchemaAddColumnAstMetadata()
) : ISchemaAddColumnAst {
    override var columnAst: ISchemaColumnAst? = null
}
