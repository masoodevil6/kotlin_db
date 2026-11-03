package gog.my_project.data_base_schema.renderer.nodes.create_table.table

import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.dialect.data_class.create_table.table.SchemaTableData
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_table.table.ISchemaTableCapability

class MySqlSchemaTableCapability: ISchemaTableCapability {

    override fun render(
        ast: ISchemaTableAst,
        ctx: IRenderContext,
        dataClass: SchemaTableData?
    ): String? {
        val tableName = requireNotNull(ast.metadata.tableName) { "Table name is required" }
        return "`${tableName.replace("`", "``")}`"
    }

}
