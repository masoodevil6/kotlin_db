package gog.my_project.data_base.migration.renderer.nodes.create_full_text_index

import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_full_text_index.IMigrationCreateFullTextIndexCapability

class MySqlMigrationCreateFullTextIndexCapability : IMigrationCreateFullTextIndexCapability {
    override fun render(
        ast: IMigrationCreateFullTextIndexAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val columns = requireNotNull(ast.metadata.columnNames) { "CREATE FULLTEXT INDEX requires at least one column" }
        require(columns.isNotEmpty()) { "CREATE FULLTEXT INDEX requires at least one column" }

        val renderedColumns = columns.joinToString(", ") { quoteIdentifier(it) }
        return "CREATE FULLTEXT INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} ($renderedColumns)"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE FULLTEXT INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
