package gog.my_project.data_base.migration.api.interfaces.drop_table

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst

interface IMigrationDropTableApi : IMigrationApi<IMigrationDropTableAst> {

    fun tableName(table: String): IMigrationDropTableApi

    fun ifExists(): IMigrationDropTableApi
}