package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableApi
import gog.my_project.data_base.migration.builder.ast.create_table.render_migration_create_table.MigrationRenderCreateTableBuilder

fun createTable(
    block: IMigrationRenderCreateTableApi.() -> Unit
): IMigrationRenderCreateTableApi = MigrationRenderCreateTableBuilder().apply(block)
