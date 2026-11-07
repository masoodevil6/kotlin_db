package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base_schema.*

import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.api.builder.migration

class CreateCustomersMigrationV1 : Migration {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.createTable {
            table { tableName("customers") }
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
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable {
            tableName("customers")
        })
    }
}
