package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.builder.ast.modify_column.MigrationModifyColumnBuilder

fun modifyColumn(block: IMigrationModifyColumnApi.() -> Unit): IMigrationModifyColumnApi =
    MigrationModifyColumnBuilder().apply(block)
