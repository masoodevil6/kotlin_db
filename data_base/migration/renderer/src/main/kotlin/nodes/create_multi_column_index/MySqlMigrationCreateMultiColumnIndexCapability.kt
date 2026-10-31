package gog.my_project.data_base.migration.renderer.nodes.create_multi_column_index

import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_multi_column_index.IMigrationCreateMultiColumnIndexCapability

class MySqlMigrationCreateMultiColumnIndexCapability : IMigrationCreateMultiColumnIndexCapability {
    override fun render(
        ast: IMigrationCreateMultiColumnIndexAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val columns = requireNotNull(ast.metadata.columnNames) { "CREATE INDEX requires a column list" }
        require(columns.isNotEmpty()) { "CREATE INDEX requires at least one column" }

        val renderedColumns = columns.joinToString(", ") { quoteIdentifier(it) }
        return "CREATE INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} ($renderedColumns)"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
