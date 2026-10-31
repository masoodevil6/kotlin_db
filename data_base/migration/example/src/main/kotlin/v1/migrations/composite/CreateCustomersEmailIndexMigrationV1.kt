package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.builder.migration

class CreateCustomersEmailIndexMigrationV1 : Migration {
    override fun up(): MigrationDefinition = migration {
        createIndex {
            tableName("customers")
            name("idx_customers_email")
            column("email")
        }
    }

    override fun down(): MigrationDefinition = migration {
        dropIndex {
            tableName("customers")
            name("idx_customers_email")
        }
    }
}
