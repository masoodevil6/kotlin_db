package gog.my_project.data_base_schema.ast.schema.drop_column

import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst
import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.ast.metadata.drop_column.SchemaDropColumnAstMetadata

class SchemaDropColumnAst(
    override var metadata: SchemaDropColumnAstMetadata = SchemaDropColumnAstMetadata()
) : ISchemaDropColumnAst {
    override var ifExistAst: ISchemaIfExistAst? = null
}
