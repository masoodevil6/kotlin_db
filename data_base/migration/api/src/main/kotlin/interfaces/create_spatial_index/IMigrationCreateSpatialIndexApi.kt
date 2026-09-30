package gog.my_project.data_base.migration.api.interfaces.create_spatial_index

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst

interface IMigrationCreateSpatialIndexApi : IMigrationApi<IMigrationCreateSpatialIndexAst> {
    fun tableName(table: String): IMigrationCreateSpatialIndexApi
    fun name(name: String): IMigrationCreateSpatialIndexApi
    fun column(column: String): IMigrationCreateSpatialIndexApi
}
