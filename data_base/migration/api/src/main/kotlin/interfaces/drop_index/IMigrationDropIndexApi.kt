package gog.my_project.data_base.migration.api.interfaces.drop_index

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst

interface IMigrationDropIndexApi : IMigrationApi<IMigrationDropIndexAst> {
    fun tableName(table: String): IMigrationDropIndexApi
    fun name(name: String): IMigrationDropIndexApi
}
