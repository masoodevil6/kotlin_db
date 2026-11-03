package gog.my_project.data_base_schema.renderer.nodes.drop_index

import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.drop_index.ISchemaDropIndexCapability

class MySqlSchemaDropIndexCapability : ISchemaDropIndexCapability {
    override fun render(
        ast: ISchemaDropIndexAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        return "DROP INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)}"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "DROP INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
