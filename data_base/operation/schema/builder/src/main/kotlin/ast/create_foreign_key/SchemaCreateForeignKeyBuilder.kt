package gog.my_project.data_base_schema.builder.ast.create_foreign_key

import gog.my_project.data_base_schema.api.interfaces.create_foreign_key.ISchemaCreateForeignKeyApi
import gog.my_project.data_base_schema.ast.interfaces.create_foreign_key.ISchemaCreateForeignKeyAst
import gog.my_project.data_base_schema.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base_schema.ast.schema.create_foreign_key.SchemaCreateForeignKeyAst

class SchemaCreateForeignKeyBuilder(
    override var ast: ISchemaCreateForeignKeyAst = SchemaCreateForeignKeyAst(),
) : ISchemaCreateForeignKeyApi {
    override fun tableName(table: String): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.constraintName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.columnNames = columns.map(String::trim)
    }

    override fun referencesTable(table: String): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.referencesTableName = table.trim()
    }

    override fun referencesColumns(vararg columns: String): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.referencesColumnNames = columns.map(String::trim)
    }

    override fun onDelete(action: ForeignKeyAction): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.onDelete = action
    }

    override fun onUpdate(action: ForeignKeyAction): ISchemaCreateForeignKeyApi = apply {
        ast.metadata.onUpdate = action
    }

    fun build(): ISchemaCreateForeignKeyApi {
        return this
    }
}
