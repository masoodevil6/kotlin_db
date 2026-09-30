package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_spatial_index.IMigrationCreateSpatialIndexApi
import gog.my_project.data_base.migration.builder.ast.create_spatial_index.MigrationCreateSpatialIndexBuilder

fun createSpatialIndex(
    block: IMigrationCreateSpatialIndexApi.() -> Unit,
): IMigrationCreateSpatialIndexApi {
    val builder = MigrationCreateSpatialIndexBuilder()
    builder.block()
    return builder.build()
}
