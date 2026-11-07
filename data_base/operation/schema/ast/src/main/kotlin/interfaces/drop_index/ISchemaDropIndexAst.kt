package gog.my_project.data_base_schema.ast.interfaces.drop_index

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.ast.metadata.drop_index.SchemaDropIndexAstMetadata

interface ISchemaDropIndexAst : ISchemaAst<SchemaDropIndexAstMetadata> {
    var ifExistAst: ISchemaIfExistAst?
}
