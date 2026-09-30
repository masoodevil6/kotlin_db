package gog.my_project.data_base.migration.renderer.nodes.create_spatial_index

import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_spatial_index.IMigrationCreateSpatialIndexCapability

class MySqlMigrationCreateSpatialIndexCapability : IMigrationCreateSpatialIndexCapability {
    override fun render(
        ast: IMigrationCreateSpatialIndexAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val index = requireIdentifier(ast.indexName, "index")
        val table = requireIdentifier(ast.tableName, "table")
        val column = requireIdentifier(ast.columnName, "column")

        return "CREATE SPATIAL INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} (${quoteIdentifier(column)})"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) {
            "CREATE SPATIAL INDEX requires a non-blank $label name"
        }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
