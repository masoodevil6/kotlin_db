package gog.my_project.data_base.migration.api.interfaces.create_index

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_index.IMigrationCreateIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod

interface IMigrationCreateIndexApi : IMigrationApi<IMigrationCreateIndexAst> {
    fun tableName(table: String): IMigrationCreateIndexApi
    fun name(name: String): IMigrationCreateIndexApi
    fun column(column: String): IMigrationCreateIndexApi
    fun using(method: IndexMethod): IMigrationCreateIndexApi
}
