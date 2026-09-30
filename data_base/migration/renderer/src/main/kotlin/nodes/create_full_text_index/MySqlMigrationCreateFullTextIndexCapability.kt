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
        val index = requireIdentifier(ast.indexName, "index")
        val table = requireIdentifier(ast.tableName, "table")
        val columns = requireNotNull(ast.columnNames) { "CREATE FULLTEXT INDEX requires at least one column" }
        require(columns.isNotEmpty()) { "CREATE FULLTEXT INDEX requires at least one column" }
        require(columns.all(String::isNotBlank)) { "CREATE FULLTEXT INDEX column names must be non-blank" }
        requireNoDuplicateColumns(columns)

        val renderedColumns = columns.joinToString(", ") { quoteIdentifier(it) }
        return "CREATE FULLTEXT INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} ($renderedColumns)"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) {
            "CREATE FULLTEXT INDEX requires a non-blank $label name"
        }

    private fun requireNoDuplicateColumns(columns: List<String>) {
        for (currentIndex in columns.indices) {
            for (previousIndex in 0 until currentIndex) {
                require(!columns[currentIndex].trim().equals(columns[previousIndex].trim(), ignoreCase = true)) {
                    "CREATE FULLTEXT INDEX column names must not contain duplicates"
                }
            }
        }
    }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
