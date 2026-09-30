package gog.my_project.data_base.migration.api.interfaces.create_table.definition

interface IMigrationCreateTableFullTextIndexApi {
    fun name(name: String): IMigrationCreateTableFullTextIndexApi
    fun columns(vararg columns: String): IMigrationCreateTableFullTextIndexApi
}
