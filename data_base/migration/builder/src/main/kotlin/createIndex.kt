package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.builder.ast.create_index.MigrationCreateIndexBuilder

fun createIndex(block: IMigrationCreateIndexApi.() -> Unit): IMigrationCreateIndexApi {
    val builder = MigrationCreateIndexBuilder()
    builder.block()
    return builder.build()
}
