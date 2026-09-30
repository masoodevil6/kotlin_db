package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.builder.ast.drop_foreign_key.MigrationDropForeignKeyBuilder

fun dropForeignKey(block: IMigrationDropForeignKeyApi.() -> Unit): IMigrationDropForeignKeyApi {
    val builder = MigrationDropForeignKeyBuilder()
    builder.block()
    return builder.build()
}
