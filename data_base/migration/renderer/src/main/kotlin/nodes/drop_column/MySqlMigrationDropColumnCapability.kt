package gog.my_project.data_base.migration.renderer.nodes.drop_column

import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.drop_column.IMigrationDropColumnCapability

class MySqlMigrationDropColumnCapability : IMigrationDropColumnCapability {
    override fun render(
        ast: IMigrationDropColumnAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val tableName = requireNotNull(ast.tableName?.takeIf(String::isNotBlank)) {
            "DROP COLUMN requires a non-blank table name"
        }
        val columnName = requireNotNull(ast.name?.takeIf(String::isNotBlank)) {
            "DROP COLUMN requires a non-blank column name"
        }

        return "ALTER TABLE ${quoteIdentifier(tableName)} DROP COLUMN ${quoteIdentifier(columnName)}"
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}
