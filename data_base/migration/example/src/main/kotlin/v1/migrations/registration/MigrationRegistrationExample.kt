package gog.my_project.data_base.migration.example.v1.migrations.registration

import gog.my_project.data_base_schema.*

import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base.core.annotations.migrations.MigrationId
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_migration.api.builder.migrationConfig

object migrationTags {
    const val create_customers = "create_customers"
    const val add_customer_email = "add_customer_email"
}

@MigrationId(migrationTags.create_customers)
class CreateCustomersRegistrationExample : Migration {
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
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable { tableName("customers") })
    }
}

@MigrationId(migrationTags.add_customer_email)
class AddCustomerEmailRegistrationExample : Migration {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.addColumn {
            tableName("customers")
            name("email")
            dataType(SchemaDataType.VarcharType(255))
            notNull()
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropColumn {
            tableName("customers")
            name("email")
        })
    }
}

fun customerMigrationConfiguration() = migrationConfig {
    migrationVersion("customers_v1") {
        migration<CreateCustomersRegistrationExample>()

        migrationGroup("customers") {
            migration<AddCustomerEmailRegistrationExample>()
        }
    }
}
