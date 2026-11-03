package gog.my_project.data_base_schema.ast.schema.drop_foreign_key

import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.ast.metadata.drop_foreign_key.SchemaDropForeignKeyAstMetadata

class SchemaDropForeignKeyAst(
    override var metadata: SchemaDropForeignKeyAstMetadata = SchemaDropForeignKeyAstMetadata()
) : ISchemaDropForeignKeyAst {

}
