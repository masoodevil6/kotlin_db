package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.builder.ast.create_foreign_key.MigrationCreateForeignKeyBuilder

fun createForeignKey(block: IMigrationCreateForeignKeyApi.() -> Unit): IMigrationCreateForeignKeyApi {
    val builder = MigrationCreateForeignKeyBuilder()
    builder.block()
    return builder.build()
}
