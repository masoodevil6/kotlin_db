package gog.my_project.data_base_schema.renderer.nodes.drop_table

import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.drop_table.ISchemaDropTableCapability

class MySqlSchemaDropTableCapability : ISchemaDropTableCapability {

    override fun render(
        ast: ISchemaDropTableAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?
    ): String {
        val tableName = requireNotNull(ast.metadata.tableName) {
            "DROP TABLE requires a table name"
        }
        val quotedTableName = "`${tableName.replace("`", "``")}`"
        val ifExists = if (ast.metadata.ifExists) " IF EXISTS" else ""
        return "DROP TABLE$ifExists $quotedTableName"
    }
}
