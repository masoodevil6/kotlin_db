package gog.my_project.data_base.migration.builder.ast.create_foreign_key

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.ast.schema.create_foreign_key.MigrationCreateForeignKeyAst

class MigrationCreateForeignKeyBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateForeignKeyAst = MigrationCreateForeignKeyAst(),
) : IMigrationCreateForeignKeyApi {
    override fun tableName(table: String): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.constraintName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.columnNames = columns.map(String::trim)
    }

    override fun referencesTable(table: String): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.referencesTableName = table.trim()
    }

    override fun referencesColumns(vararg columns: String): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.referencesColumnNames = columns.map(String::trim)
    }

    override fun onDelete(action: ForeignKeyAction): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.onDelete = action
    }

    override fun onUpdate(action: ForeignKeyAction): IMigrationCreateForeignKeyApi = apply {
        ast.metadata.onUpdate = action
    }

    fun build(): IMigrationCreateForeignKeyApi {
        return this
    }
}
