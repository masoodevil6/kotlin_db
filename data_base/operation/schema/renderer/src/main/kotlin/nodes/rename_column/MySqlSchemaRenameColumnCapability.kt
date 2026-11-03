package gog.my_project.data_base_schema.renderer.nodes.rename_column

import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.rename_column.ISchemaRenameColumnCapability
import gog.my_project.data_base_schema.dialect.nodes.rename_column.IRenameColumnSqlDialect
import gog.my_project.data_base_schema.dialect.nodes.rename_column.RenameColumnSqlStrategy

class MySqlSchemaRenameColumnCapability : ISchemaRenameColumnCapability {
    override fun render(ast: ISchemaRenameColumnAst, ctx: IRenderContext, dataClass: SchemaDataClass?): String {
        validateAst(ast)
        val sqlDialect = ctx.dialect as? IRenameColumnSqlDialect
            ?: error("The selected dialect does not expose RENAME COLUMN SQL strategy")
        if (sqlDialect.renameColumnSqlStrategy == RenameColumnSqlStrategy.CHANGE_COLUMN) {
            error("The configured target requires CHANGE COLUMN; use the migration executor to resolve the source definition")
        }
        val (table, source, target) = validateAst(ast)
        return "ALTER TABLE ${quote(table)} RENAME COLUMN ${quote(source)} TO ${quote(target)}"
    }

    fun renderLegacy(ast: ISchemaRenameColumnAst, definitionSuffix: String): String {
        val (table, source, target) = validateAst(ast)
        require(definitionSuffix.isNotBlank() && definitionSuffix.first().isWhitespace()) {
            "Resolved source column definition is empty or malformed"
        }
        return "ALTER TABLE ${quote(table)} CHANGE COLUMN ${quote(source)} ${quote(target)}$definitionSuffix"
    }

    private fun validateAst(ast: ISchemaRenameColumnAst): Triple<String, String, String> {
        val table = requireNotNull(ast.metadata.tableName) { "RENAME COLUMN requires a table name" }
        val source = requireNotNull(ast.metadata.name) { "RENAME COLUMN requires a source column name" }
        val target = requireNotNull(ast.metadata.to) { "RENAME COLUMN requires a target column name" }
        return Triple(table, source, target)
    }

    private fun quote(value: String) = "`${value.replace("`", "``")}`"
}
