package gog.my_project.data_base_schema.api.interfaces.create_table.definition

interface ISchemaCreateTableFullTextIndexApi {
    fun name(name: String): ISchemaCreateTableFullTextIndexApi
    fun columns(vararg columns: String): ISchemaCreateTableFullTextIndexApi
}
