package gog.my_project.data_base_schema.api.interfaces.create_spatial_index

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_spatial_index.ISchemaCreateSpatialIndexAst

interface ISchemaCreateSpatialIndexApi : ISchemaApi<ISchemaCreateSpatialIndexAst> {
    fun tableName(table: String): ISchemaCreateSpatialIndexApi
    fun name(name: String): ISchemaCreateSpatialIndexApi
    fun column(column: String): ISchemaCreateSpatialIndexApi
}
