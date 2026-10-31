package gog.my_project.data_base.migration.example.v1.migrations.registration

import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.builder.migrationConfig
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.VarcharType

object migrationTags {
    const val create_customers = "create_customers"
    const val add_customer_email = "add_customer_email"
}

@MigrationId(migrationTags.create_customers)
class CreateCustomersRegistrationExample : Migration {
    override fun up(): MigrationDefinition = migration {
        createTable {
            table { tableName("customers") }
            addColumn {
                name("id")
                dataType(IntType())
                notNull()
                autoIncrement()
                primaryKey()
            }
        }
    }

    override fun down(): MigrationDefinition = migration {
        dropTable { tableName("customers") }
    }
}

@MigrationId(migrationTags.add_customer_email)
class AddCustomerEmailRegistrationExample : Migration {
    override fun up(): MigrationDefinition = migration {
        addColumn {
            tableName("customers")
            name("email")
            dataType(VarcharType(255))
            notNull()
        }
    }

    override fun down(): MigrationDefinition = migration {
        dropColumn {
            tableName("customers")
            name("email")
        }
    }
}

fun customerMigrationConfiguration() = migrationConfig {
    migration<CreateCustomersRegistrationExample>()

    migrationGroup("customers") {
        migration<AddCustomerEmailRegistrationExample>()
    }
}
