package gog.my_project.data_base.migration.renderer.nodes.add_column

import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.data_class.create_table.column.MigrationColumnData
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.add_column.IMigrationAddColumnCapability
import gog.my_project.data_base.migration.params.data_types.IntType

class MySqlMigrationAddColumnCapability : IMigrationAddColumnCapability {
    override fun render(
        ast: IMigrationAddColumnAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val tableName = requireNotNull(ast.tableName?.trim()?.takeIf(String::isNotBlank)) {
            "ADD COLUMN requires a non-blank table name"
        }
        val column = requireNotNull(ast.columnAst) {
            "ADD COLUMN requires a column definition"
        }
        val columnName = requireNotNull(column.columnName?.trim()?.takeIf(String::isNotBlank)) {
            "ADD COLUMN requires a non-blank column name"
        }
        val type = requireNotNull(column.columnDataType) {
            "Column '$columnName' must have a data type"
        }

        if (column.columnAutoIncrement) {
            require(type is IntType) { "AUTO_INCREMENT column '$columnName' must use IntType" }
            require(column.columnPrimary) { "AUTO_INCREMENT column '$columnName' must be a primary key" }
        }
        require(!(column.columnPrimary && column.columnNullable)) {
            "Primary key column '$columnName' cannot be nullable"
        }
        val columnSql = requireNotNull(
            ctx.registry.render(column, ctx.dialect, MigrationColumnData()),
        ) { "Renderer returned no SQL for column '$columnName'" }

        return "ALTER TABLE ${quoteIdentifier(tableName)} ADD COLUMN $columnSql"
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}