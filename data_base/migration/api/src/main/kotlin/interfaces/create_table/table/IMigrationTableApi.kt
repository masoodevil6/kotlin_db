package gog.my_project.data_base.migration.api.interfaces.create_table.table

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst

interface IMigrationTableApi: IMigrationApi<IMigrationTableAst> {

    fun tableName(table: String): IMigrationTableApi;

}