package gog.my_project.data_base.migration.api.interfaces.create_unique_index

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst

interface IMigrationCreateUniqueIndexApi : IMigrationApi<IMigrationCreateUniqueIndexAst> {
    fun tableName(table: String): IMigrationCreateUniqueIndexApi
    fun name(name: String): IMigrationCreateUniqueIndexApi
    fun column(column: String): IMigrationCreateUniqueIndexApi
}
