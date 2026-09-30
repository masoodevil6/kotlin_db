package gog.my_project.data_base.migration.renderer.nodes.drop_index

import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.drop_index.IMigrationDropIndexCapability

class MySqlMigrationDropIndexCapability : IMigrationDropIndexCapability {
    override fun render(
        ast: IMigrationDropIndexAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val index = requireIdentifier(ast.indexName, "index")
        val table = requireIdentifier(ast.tableName, "table")
        return "DROP INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)}"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) {
            "DROP INDEX requires a non-blank $label name"
        }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
