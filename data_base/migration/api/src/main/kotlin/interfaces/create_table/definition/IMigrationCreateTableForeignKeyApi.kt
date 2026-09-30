package gog.my_project.data_base.migration.api.interfaces.create_table.definition

import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

interface IMigrationCreateTableForeignKeyApi {
    fun name(name: String): IMigrationCreateTableForeignKeyApi
    fun columns(vararg columns: String): IMigrationCreateTableForeignKeyApi
    fun referencesTable(table: String): IMigrationCreateTableForeignKeyApi
    fun referencesColumns(vararg columns: String): IMigrationCreateTableForeignKeyApi
    fun onDelete(action: ForeignKeyAction): IMigrationCreateTableForeignKeyApi
    fun onUpdate(action: ForeignKeyAction): IMigrationCreateTableForeignKeyApi
}
