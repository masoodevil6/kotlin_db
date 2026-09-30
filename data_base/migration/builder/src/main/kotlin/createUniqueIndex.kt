package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.builder.ast.create_unique_index.MigrationCreateUniqueIndexBuilder

fun createUniqueIndex(block: IMigrationCreateUniqueIndexApi.() -> Unit): IMigrationCreateUniqueIndexApi {
    val builder = MigrationCreateUniqueIndexBuilder()
    builder.block()
    return builder.build()
}
