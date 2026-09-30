package gog.my_project.data_base.migration.renderer.nodes.rename_table

import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.rename_table.IMigrationRenameTableCapability

class MySqlMigrationRenameTableCapability : IMigrationRenameTableCapability {

    override fun render(
        ast: IMigrationRenameTableAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?
    ): String {
        val fromTableName = requireNotNull(ast.fromTableName?.takeIf(String::isNotBlank)) {
            "RENAME TABLE requires a non-blank source table name"
        }
        val toTableName = requireNotNull(ast.toTableName?.takeIf(String::isNotBlank)) {
            "RENAME TABLE requires a non-blank target table name"
        }
        val quotedFromTableName = "`${fromTableName.replace("`", "``")}`"
        val quotedToTableName = "`${toTableName.replace("`", "``")}`"
        return "RENAME TABLE $quotedFromTableName TO $quotedToTableName"
    }
}