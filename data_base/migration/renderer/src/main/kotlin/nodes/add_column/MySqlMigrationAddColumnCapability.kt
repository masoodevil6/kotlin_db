package gog.my_project.data_base.migration.renderer.nodes.add_column

import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.data_class.create_table.column.MigrationColumnData
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.add_column.IMigrationAddColumnCapability

class MySqlMigrationAddColumnCapability : IMigrationAddColumnCapability {
    override fun render(
        ast: IMigrationAddColumnAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val tableName = requireNotNull(ast.metadata.tableName) {
            "ADD COLUMN requires a table name"
        }
        val column = requireNotNull(ast.columnAst) {
            "ADD COLUMN requires a column definition"
        }
        val columnName = requireNotNull(column.metadata.columnName) {
            "ADD COLUMN requires a column name"
        }
        val type = requireNotNull(column.metadata.columnDataType) {
            "Column '$columnName' must have a data type"
        }

        val columnSql = requireNotNull(
            ctx.registry.render(column, ctx.dialect, MigrationColumnData()),
        ) { "Renderer returned no SQL for column '$columnName'" }

        return "ALTER TABLE ${quoteIdentifier(tableName)} ADD COLUMN $columnSql"
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}
