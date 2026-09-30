package gog.my_project.data_base.migration.api.interfaces.drop_column

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst

interface IMigrationDropColumnApi : IMigrationApi<IMigrationDropColumnAst> {
    fun tableName(table: String): IMigrationDropColumnApi

    fun name(name: String): IMigrationDropColumnApi

    fun ifExists(): IMigrationDropColumnApi
}
