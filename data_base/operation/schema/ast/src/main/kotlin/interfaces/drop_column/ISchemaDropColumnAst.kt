package gog.my_project.data_base_schema.ast.interfaces.drop_column

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.ast.metadata.drop_column.SchemaDropColumnAstMetadata

interface ISchemaDropColumnAst : ISchemaAst<SchemaDropColumnAstMetadata> {
    var ifExistAst: ISchemaIfExistAst?
}
