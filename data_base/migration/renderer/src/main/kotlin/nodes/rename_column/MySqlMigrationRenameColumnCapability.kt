package gog.my_project.data_base.migration.renderer.nodes.rename_column

import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.rename_column.IMigrationRenameColumnCapability
import gog.my_project.data_base.migration.dialect.nodes.rename_column.IRenameColumnSqlDialect
import gog.my_project.data_base.migration.dialect.nodes.rename_column.RenameColumnSqlStrategy

class MySqlMigrationRenameColumnCapability : IMigrationRenameColumnCapability {
    override fun render(ast: IMigrationRenameColumnAst, ctx: IRenderContext, dataClass: MigrationDataClass?): String {
        validateAst(ast)
        val sqlDialect = ctx.dialect as? IRenameColumnSqlDialect
            ?: error("The selected dialect does not expose RENAME COLUMN SQL strategy")
        if (sqlDialect.renameColumnSqlStrategy == RenameColumnSqlStrategy.CHANGE_COLUMN) {
            error("The configured target requires CHANGE COLUMN; use the migration executor to resolve the source definition")
        }
        val (table, source, target) = validateAst(ast)
        return "ALTER TABLE ${quote(table)} RENAME COLUMN ${quote(source)} TO ${quote(target)}"
    }

    fun renderLegacy(ast: IMigrationRenameColumnAst, definitionSuffix: String): String {
        val (table, source, target) = validateAst(ast)
        require(definitionSuffix.isNotBlank() && definitionSuffix.first().isWhitespace()) {
            "Resolved source column definition is empty or malformed"
        }
        return "ALTER TABLE ${quote(table)} CHANGE COLUMN ${quote(source)} ${quote(target)}$definitionSuffix"
    }

    private fun validateAst(ast: IMigrationRenameColumnAst): Triple<String, String, String> {
        val table = requireNotNull(ast.tableName?.trim()?.takeIf(String::isNotBlank)) { "RENAME COLUMN requires a non-blank table name" }
        val source = requireNotNull(ast.name?.trim()?.takeIf(String::isNotBlank)) { "RENAME COLUMN requires a non-blank source column name" }
        val target = requireNotNull(ast.to?.trim()?.takeIf(String::isNotBlank)) { "RENAME COLUMN requires a non-blank target column name" }
        require(source != target) { "RENAME COLUMN source and target names must differ" }
        return Triple(table, source, target)
    }

    private fun quote(value: String) = "`${value.replace("`", "``")}`"
}
