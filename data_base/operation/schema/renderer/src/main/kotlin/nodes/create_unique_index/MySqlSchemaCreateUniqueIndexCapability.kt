package gog.my_project.data_base_schema.renderer.nodes.create_unique_index

import gog.my_project.data_base_schema.ast.interfaces.create_unique_index.ISchemaCreateUniqueIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_unique_index.ISchemaCreateUniqueIndexCapability

class MySqlSchemaCreateUniqueIndexCapability : ISchemaCreateUniqueIndexCapability {
    override fun render(
        ast: ISchemaCreateUniqueIndexAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val column = requireIdentifier(ast.metadata.columnName, "column")
        return "CREATE UNIQUE INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} (${quoteIdentifier(column)})"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE UNIQUE INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
