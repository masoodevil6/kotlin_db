package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.builder.ast.drop_table.MigrationDropTableBuilder

fun dropTable(
    block: IMigrationDropTableApi.() -> Unit
): IMigrationDropTableApi = MigrationDropTableBuilder().apply(block)