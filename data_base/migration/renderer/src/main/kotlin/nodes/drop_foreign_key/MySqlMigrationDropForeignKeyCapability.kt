package gog.my_project.data_base.migration.renderer.nodes.drop_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.drop_foreign_key.IMigrationDropForeignKeyCapability

class MySqlMigrationDropForeignKeyCapability : IMigrationDropForeignKeyCapability {
    override fun render(
        ast: IMigrationDropForeignKeyAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val table = requireIdentifier(ast.tableName, "table")
        val constraint = requireIdentifier(ast.constraintName, "constraint")
        return "ALTER TABLE ${quoteIdentifier(table)} DROP FOREIGN KEY ${quoteIdentifier(constraint)}"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) {
            "DROP FOREIGN KEY requires a non-blank $label name"
        }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
