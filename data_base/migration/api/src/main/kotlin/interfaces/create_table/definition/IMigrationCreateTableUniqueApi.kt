package gog.my_project.data_base.migration.api.interfaces.create_table.definition

interface IMigrationCreateTableUniqueApi {
    fun name(name: String): IMigrationCreateTableUniqueApi
    fun columns(vararg columns: String): IMigrationCreateTableUniqueApi
}
