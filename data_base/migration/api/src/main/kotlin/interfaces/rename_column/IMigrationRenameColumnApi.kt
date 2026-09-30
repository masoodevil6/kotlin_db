package gog.my_project.data_base.migration.api.interfaces.rename_column

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst

interface IMigrationRenameColumnApi : IMigrationApi<IMigrationRenameColumnAst> {
    fun tableName(table: String): IMigrationRenameColumnApi
    fun name(name: String): IMigrationRenameColumnApi
    fun to(name: String): IMigrationRenameColumnApi
}
