package gog.my_project.data_base.migration.api.interfaces.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod

interface IMigrationCreateTableIndexApi {
    fun name(name: String): IMigrationCreateTableIndexApi
    fun columns(vararg columns: String): IMigrationCreateTableIndexApi
    fun using(method: IndexMethod): IMigrationCreateTableIndexApi
}
