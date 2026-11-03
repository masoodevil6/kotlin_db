package gog.my_project.data_base_manager.example.migrations

import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.builder.migrationConfig
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.VarcharType

const val MAIN_CUSTOMERS_TABLE = "manager_example_customers"

@MigrationId("create_manager_example_customers")
class CreateManagerExampleCustomersMigration : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName(MAIN_CUSTOMERS_TABLE) }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
            addColumn {
                name("display_name")
                dataType(VarcharType(120))
                notNull()
            }
        }
    }

    override fun down(): MigrationDefinition = migration {
        dropTable { tableName(MAIN_CUSTOMERS_TABLE) }
    }
}

fun mainDatabaseMigrations() = migrationConfig {
    migration<CreateManagerExampleCustomersMigration>()
}
