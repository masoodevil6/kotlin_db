package gog.my_project.data_base_schema.example.operations

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.api.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableApi
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base_schema.builder.ast.drop_column.SchemaDropColumnBuilder
import gog.my_project.data_base_schema.builder.ast.modify_column.SchemaModifyColumnBuilder
import gog.my_project.data_base_schema.builder.ast.rename_column.SchemaRenameColumnBuilder
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.VarcharType

/** Examples use their own table name and only construct Schema operations; they do not execute SQL. */
object CustomerSchemaOperations {
    const val TABLE = "schema_api_example_customers"

    fun createTable(): ISchemaRenderCreateTableApi = SchemaRenderCreateTableBuilder().apply {
        table { tableName(TABLE) }
        addColumn {
            name("id")
            dataType(IntType())
            notNull()
            autoIncrement()
            primaryKey()
        }
        addColumn {
            name("email")
            dataType(VarcharType(255))
            notNull()
        }
        addColumn {
            name("legacy_code")
            dataType(VarcharType(32))
            nullable()
        }
    }

    fun addColumn(): ISchemaAddColumnApi = SchemaAddColumnBuilder().apply {
        tableName(TABLE)
        name("contact_name")
        dataType(VarcharType(120))
        nullable()
    }

    fun renameColumn(): ISchemaRenameColumnApi = SchemaRenameColumnBuilder().apply {
        tableName(TABLE)
        name("contact_name")
        to("full_name")
    }

    fun modifyColumn(): ISchemaModifyColumnApi = SchemaModifyColumnBuilder().apply {
        tableName(TABLE)
        name("full_name")
        dataType(VarcharType(180))
        notNull()
        default("Unknown")
    }

    fun dropColumn(): ISchemaDropColumnApi = SchemaDropColumnBuilder().apply {
        tableName(TABLE)
        name("legacy_code")
    }

    fun examples(): List<NamedSchemaOperation> = listOf(
        NamedSchemaOperation("create table", createTable()),
        NamedSchemaOperation("add column", addColumn()),
        NamedSchemaOperation("rename column", renameColumn()),
        NamedSchemaOperation("modify column", modifyColumn()),
        NamedSchemaOperation("drop column", dropColumn()),
    )
}

data class NamedSchemaOperation(
    val name: String,
    val operation: ISchemaApi<*>,
)
