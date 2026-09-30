package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.builder.ast.add_column.MigrationAddColumnBuilder

fun addColumn(
    block: IMigrationAddColumnApi.() -> Unit
): IMigrationAddColumnApi = MigrationAddColumnBuilder().apply(block)