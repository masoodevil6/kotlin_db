package gog.my_project.data_base_schema.api.interfaces.create_table.definition

import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction

interface ISchemaCreateTableForeignKeyApi {
    fun name(name: String): ISchemaCreateTableForeignKeyApi
    fun columns(vararg columns: String): ISchemaCreateTableForeignKeyApi
    fun referencesTable(table: String): ISchemaCreateTableForeignKeyApi
    fun referencesColumns(vararg columns: String): ISchemaCreateTableForeignKeyApi
    fun onDelete(action: ForeignKeyAction): ISchemaCreateTableForeignKeyApi
    fun onUpdate(action: ForeignKeyAction): ISchemaCreateTableForeignKeyApi
}
