package gog.my_project.data_base.migration.example.v1.migrations.registration

import gog.my_project.data_base_schema.*

import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base.core.annotations.migrations.MigrationId
import gog.my_project.data_base.migration.example.v1.migrations.DataBaseMigrationTags
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_migration.api.builder.migrationConfig


class CreateCustomersRegistrationExample : Migration(DataBaseMigrationTags.Items.MIGRATION_2) {
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

class AddCustomerEmailRegistrationExample : Migration(DataBaseMigrationTags.Items.MIGRATION_3) {
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
    migrationVersion(DataBaseMigrationTags.Versions.VERSION_1) {
        migration<CreateCustomersRegistrationExample>()

        migrationGroup(DataBaseMigrationTags.Groups.GROUP_1) {
            migration<AddCustomerEmailRegistrationExample>()
        }
    }
}
