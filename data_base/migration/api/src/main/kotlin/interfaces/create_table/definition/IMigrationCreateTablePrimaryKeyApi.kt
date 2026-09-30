package gog.my_project.data_base.migration.api.interfaces.create_table.definition

interface IMigrationCreateTablePrimaryKeyApi {
    fun columns(vararg columns: String): IMigrationCreateTablePrimaryKeyApi
}
