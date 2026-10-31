package gog.my_project.data_base.migration.renderer.nodes.drop_table

import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.drop_table.IMigrationDropTableCapability

class MySqlMigrationDropTableCapability : IMigrationDropTableCapability {

    override fun render(
        ast: IMigrationDropTableAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?
    ): String {
        val tableName = requireNotNull(ast.metadata.tableName) {
            "DROP TABLE requires a table name"
        }
        val quotedTableName = "`${tableName.replace("`", "``")}`"
        val ifExists = if (ast.metadata.ifExists) " IF EXISTS" else ""
        return "DROP TABLE$ifExists $quotedTableName"
    }
}
