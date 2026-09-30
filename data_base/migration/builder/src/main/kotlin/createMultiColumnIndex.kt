package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.builder.ast.create_multi_column_index.MigrationCreateMultiColumnIndexBuilder

fun createMultiColumnIndex(
    block: IMigrationCreateMultiColumnIndexApi.() -> Unit,
): IMigrationCreateMultiColumnIndexApi {
    val builder = MigrationCreateMultiColumnIndexBuilder()
    builder.block()
    return builder.build()
}
