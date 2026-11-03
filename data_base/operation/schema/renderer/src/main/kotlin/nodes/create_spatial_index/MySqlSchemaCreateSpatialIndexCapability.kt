package gog.my_project.data_base_schema.renderer.nodes.create_spatial_index

import gog.my_project.data_base_schema.ast.interfaces.create_spatial_index.ISchemaCreateSpatialIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_spatial_index.ISchemaCreateSpatialIndexCapability

class MySqlSchemaCreateSpatialIndexCapability : ISchemaCreateSpatialIndexCapability {
    override fun render(
        ast: ISchemaCreateSpatialIndexAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val column = requireIdentifier(ast.metadata.columnName, "column")

        return "CREATE SPATIAL INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} (${quoteIdentifier(column)})"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE SPATIAL INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
