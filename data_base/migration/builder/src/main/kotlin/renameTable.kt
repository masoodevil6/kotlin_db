package gog.my_project.data_base.migration.builder

import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
import gog.my_project.data_base.migration.builder.ast.rename_table.MigrationRenameTableBuilder

fun renameTable(
    block: IMigrationRenameTableApi.() -> Unit
): IMigrationRenameTableApi = MigrationRenameTableBuilder().apply(block)