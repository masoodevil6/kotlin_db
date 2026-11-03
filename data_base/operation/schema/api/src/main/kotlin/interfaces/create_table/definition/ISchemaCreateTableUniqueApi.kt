package gog.my_project.data_base_schema.api.interfaces.create_table.definition

interface ISchemaCreateTableUniqueApi {
    fun name(name: String): ISchemaCreateTableUniqueApi
    fun columns(vararg columns: String): ISchemaCreateTableUniqueApi
}
