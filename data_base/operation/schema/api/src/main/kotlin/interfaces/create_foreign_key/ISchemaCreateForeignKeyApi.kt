package gog.my_project.data_base_schema.api.interfaces.create_foreign_key

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_foreign_key.ISchemaCreateForeignKeyAst
import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction

interface ISchemaCreateForeignKeyApi : ISchemaApi<ISchemaCreateForeignKeyAst> {
    fun tableName(table: String): ISchemaCreateForeignKeyApi
    fun name(name: String): ISchemaCreateForeignKeyApi
    fun columns(vararg columns: String): ISchemaCreateForeignKeyApi
    fun referencesTable(table: String): ISchemaCreateForeignKeyApi
    fun referencesColumns(vararg columns: String): ISchemaCreateForeignKeyApi
    fun onDelete(action: ForeignKeyAction): ISchemaCreateForeignKeyApi
    fun onUpdate(action: ForeignKeyAction): ISchemaCreateForeignKeyApi
}
