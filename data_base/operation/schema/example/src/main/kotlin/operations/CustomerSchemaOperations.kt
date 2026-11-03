package gog.my_project.data_base_schema.example.operations

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaOperation

/** Examples use their own table name and only construct Schema operations; they do not execute SQL. */
object CustomerSchemaOperations {
    const val TABLE = "schema_api_example_customers"

    fun createTable(): SchemaOperation = Schema.createTable {
        table { tableName(TABLE) }
        addColumn {
            name("id")
            dataType(SchemaDataType.IntType)
            notNull()
            autoIncrement()
            primaryKey()
        }
        addColumn {
            name("email")
            dataType(SchemaDataType.VarcharType(255))
            notNull()
        }
        addColumn {
            name("legacy_code")
            dataType(SchemaDataType.VarcharType(32))
            nullable()
        }
    }

    fun addColumn(): SchemaOperation = Schema.addColumn {
        tableName(TABLE)
        name("contact_name")
        dataType(SchemaDataType.VarcharType(120))
        nullable()
    }

    fun renameColumn(): SchemaOperation = Schema.renameColumn {
        tableName(TABLE)
        name("contact_name")
        to("full_name")
    }

    fun modifyColumn(): SchemaOperation = Schema.modifyColumn {
        tableName(TABLE)
        name("full_name")
        dataType(SchemaDataType.VarcharType(180))
        notNull()
        default("Unknown")
    }

    fun dropColumn(): SchemaOperation = Schema.dropColumn {
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
    val operation: SchemaOperation,
)
