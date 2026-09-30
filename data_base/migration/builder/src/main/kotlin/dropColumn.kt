package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.builder.ast.drop_column.MigrationDropColumnBuilder

fun dropColumn(
    block: IMigrationDropColumnApi.() -> Unit,
): IMigrationDropColumnApi {
    val builder = MigrationDropColumnBuilder()
    builder.block()
    return builder.build()
}
