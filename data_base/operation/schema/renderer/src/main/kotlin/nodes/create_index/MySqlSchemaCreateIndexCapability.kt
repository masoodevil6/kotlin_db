package gog.my_project.data_base_schema.renderer.nodes.create_index

import gog.my_project.data_base_schema.ast.interfaces.create_index.ISchemaCreateIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_index.ISchemaCreateIndexCapability

class MySqlSchemaCreateIndexCapability : ISchemaCreateIndexCapability {
    override fun render(
        ast: ISchemaCreateIndexAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val column = requireIdentifier(ast.metadata.columnName, "column")
        val indexMethod = ast.metadata.indexMethod?.let { " USING ${it.name}" }.orEmpty()
        return "CREATE INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} (${quoteIdentifier(column)})$indexMethod"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
