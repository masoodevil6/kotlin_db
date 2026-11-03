package gog.my_project.data_base_schema.api.interfaces.create_table.definition

interface ISchemaCreateTablePrimaryKeyApi {
    fun columns(vararg columns: String): ISchemaCreateTablePrimaryKeyApi
}
