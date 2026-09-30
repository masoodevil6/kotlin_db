package gog.my_project.data_base.migration.builder.ast.create_spatial_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.ast.schema.create_spatial_index.MigrationCreateSpatialIndexAst

class MigrationCreateSpatialIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateSpatialIndexAst = MigrationCreateSpatialIndexAst(),
) : IMigrationCreateSpatialIndexApi {
    override fun tableName(table: String): IMigrationCreateSpatialIndexApi = apply {
        ast.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateSpatialIndexApi = apply {
        ast.indexName = name.trim()
    }

    override fun column(column: String): IMigrationCreateSpatialIndexApi = apply {
        ast.columnName = column.trim()
    }

    fun build(): IMigrationCreateSpatialIndexApi {
        requireIdentifier(ast.tableName, "table")
        requireIdentifier(ast.indexName, "index")
        requireIdentifier(ast.columnName, "column")
        return this
    }

    private fun requireIdentifier(value: String?, label: String) {
        require(!value.isNullOrBlank()) { "CREATE SPATIAL INDEX requires a non-blank $label name" }
    }
}
