package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.builder.ast.drop_index.MigrationDropIndexBuilder

fun dropIndex(block: IMigrationDropIndexApi.() -> Unit): IMigrationDropIndexApi {
    val builder = MigrationDropIndexBuilder()
    builder.block()
    return builder.build()
}
