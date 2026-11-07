package gog.my_project.data_base_schema.builder.ast.if_exist

import gog.my_project.data_base_schema.api.interfaces.if_exist.ISchemaIfExistApi
import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.ast.schema.if_exist.SchemaIfExistAst

class SchemaIfExistBuilder(
    override var ast: ISchemaIfExistAst = SchemaIfExistAst(),
) : ISchemaIfExistApi {
    fun build(): ISchemaIfExistApi = this
}
