package gog.my_project.data_base_schema.builder.ast.create_spatial_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.create_spatial_index.ISchemaCreateSpatialIndexApi
import gog.my_project.data_base_schema.ast.interfaces.create_spatial_index.ISchemaCreateSpatialIndexAst
import gog.my_project.data_base_schema.ast.schema.create_spatial_index.SchemaCreateSpatialIndexAst

class SchemaCreateSpatialIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaCreateSpatialIndexAst = SchemaCreateSpatialIndexAst(),
) : ISchemaCreateSpatialIndexApi {
    override fun tableName(table: String): ISchemaCreateSpatialIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaCreateSpatialIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun column(column: String): ISchemaCreateSpatialIndexApi = apply {
        ast.metadata.columnName = column.trim()
    }

    fun build(): ISchemaCreateSpatialIndexApi = this
}
