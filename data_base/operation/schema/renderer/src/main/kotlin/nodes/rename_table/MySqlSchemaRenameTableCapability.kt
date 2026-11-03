package gog.my_project.data_base_schema.renderer.nodes.rename_table

import gog.my_project.data_base_schema.ast.interfaces.rename_table.ISchemaRenameTableAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.rename_table.ISchemaRenameTableCapability

class MySqlSchemaRenameTableCapability : ISchemaRenameTableCapability {

    override fun render(
        ast: ISchemaRenameTableAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?
    ): String {
        val fromTableName = requireNotNull(ast.metadata.fromTableName) {
            "RENAME TABLE requires a source table name"
        }
        val toTableName = requireNotNull(ast.metadata.toTableName) {
            "RENAME TABLE requires a target table name"
        }
        val quotedFromTableName = "`${fromTableName.replace("`", "``")}`"
        val quotedToTableName = "`${toTableName.replace("`", "``")}`"
        return "RENAME TABLE $quotedFromTableName TO $quotedToTableName"
    }
}
