package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.builder.ast.rename_column.MigrationRenameColumnBuilder

fun renameColumn(block: IMigrationRenameColumnApi.() -> Unit): IMigrationRenameColumnApi =
    MigrationRenameColumnBuilder().apply(block)
