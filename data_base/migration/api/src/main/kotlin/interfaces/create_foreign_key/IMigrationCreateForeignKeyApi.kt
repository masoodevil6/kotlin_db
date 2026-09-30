package gog.my_project.data_base.migration.api.interfaces.create_foreign_key

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction

interface IMigrationCreateForeignKeyApi : IMigrationApi<IMigrationCreateForeignKeyAst> {
    fun tableName(table: String): IMigrationCreateForeignKeyApi
    fun name(name: String): IMigrationCreateForeignKeyApi
    fun columns(vararg columns: String): IMigrationCreateForeignKeyApi
    fun referencesTable(table: String): IMigrationCreateForeignKeyApi
    fun referencesColumns(vararg columns: String): IMigrationCreateForeignKeyApi
    fun onDelete(action: ForeignKeyAction): IMigrationCreateForeignKeyApi
    fun onUpdate(action: ForeignKeyAction): IMigrationCreateForeignKeyApi
}
