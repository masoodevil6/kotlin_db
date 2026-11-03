package gog.my_project.data_base_schema.renderer.nodes.create_multi_column_index

import gog.my_project.data_base_schema.ast.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_multi_column_index.ISchemaCreateMultiColumnIndexCapability

class MySqlSchemaCreateMultiColumnIndexCapability : ISchemaCreateMultiColumnIndexCapability {
    override fun render(
        ast: ISchemaCreateMultiColumnIndexAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
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
