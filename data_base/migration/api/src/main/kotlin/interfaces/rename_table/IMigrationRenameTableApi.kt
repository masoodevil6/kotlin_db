package gog.my_project.data_base.migration.api.interfaces.rename_table

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst

interface IMigrationRenameTableApi : IMigrationApi<IMigrationRenameTableAst> {

    fun fromTableName(table: String): IMigrationRenameTableApi

    fun toTableName(table: String): IMigrationRenameTableApi
}