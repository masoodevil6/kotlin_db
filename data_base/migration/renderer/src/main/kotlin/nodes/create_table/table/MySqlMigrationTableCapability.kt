package gog.my_project.data_base.migration.renderer.nodes.create_table.table

import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.dialect.data_class.create_table.table.MigrationTableData
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_table.table.IMigrationTableCapability

class MySqlMigrationTableCapability: IMigrationTableCapability {

    override fun render(
        ast: IMigrationTableAst,
        ctx: IRenderContext,
        dataClass: MigrationTableData?
    ): String? {
        val tableName = requireNotNull(ast.metadata.tableName) { "Table name is required" }
        return "`${tableName.replace("`", "``")}`"
    }

}
