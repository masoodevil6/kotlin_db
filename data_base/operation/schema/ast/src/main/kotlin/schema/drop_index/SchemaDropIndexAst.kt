package gog.my_project.data_base_schema.ast.schema.drop_index

import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst
import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.ast.metadata.drop_index.SchemaDropIndexAstMetadata

class SchemaDropIndexAst(
    override var metadata: SchemaDropIndexAstMetadata = SchemaDropIndexAstMetadata(),
) : ISchemaDropIndexAst {
    override var ifExistAst: ISchemaIfExistAst? = null
}
