package gog.my_project.data_base_schema.ast.schema.create_foreign_key

import gog.my_project.data_base_schema.ast.interfaces.create_foreign_key.ISchemaCreateForeignKeyAst
import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base_schema.ast.metadata.create_foreign_key.SchemaCreateForeignKeyAstMetadata

class SchemaCreateForeignKeyAst(
    override var metadata: SchemaCreateForeignKeyAstMetadata = SchemaCreateForeignKeyAstMetadata()
) : ISchemaCreateForeignKeyAst {

}
