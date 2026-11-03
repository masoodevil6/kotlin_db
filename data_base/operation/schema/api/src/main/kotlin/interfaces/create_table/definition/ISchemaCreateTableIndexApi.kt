package gog.my_project.data_base_schema.api.interfaces.create_table.definition

import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod

interface ISchemaCreateTableIndexApi {
    fun name(name: String): ISchemaCreateTableIndexApi
    fun columns(vararg columns: String): ISchemaCreateTableIndexApi
    fun using(method: IndexMethod): ISchemaCreateTableIndexApi
}
