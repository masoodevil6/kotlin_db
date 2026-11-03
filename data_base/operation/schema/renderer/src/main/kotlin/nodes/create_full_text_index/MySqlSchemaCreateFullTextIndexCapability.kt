package gog.my_project.data_base_schema.renderer.nodes.create_full_text_index

import gog.my_project.data_base_schema.ast.interfaces.create_full_text_index.ISchemaCreateFullTextIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_full_text_index.ISchemaCreateFullTextIndexCapability

class MySqlSchemaCreateFullTextIndexCapability : ISchemaCreateFullTextIndexCapability {
    override fun render(
        ast: ISchemaCreateFullTextIndexAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
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
