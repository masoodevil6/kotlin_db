package gog.my_project.data_base_schema.renderer.nodes.if_exist

import gog.my_project.data_base_schema.ast.interfaces.if_exist.ISchemaIfExistAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.if_exist.ISchemaIfExistCapability

class MySqlSchemaIfExistCapability : ISchemaIfExistCapability {
    override fun render(
        ast: ISchemaIfExistAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String = "IF EXISTS"
}
