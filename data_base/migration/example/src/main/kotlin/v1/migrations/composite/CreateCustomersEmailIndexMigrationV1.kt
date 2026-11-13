package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base.migration.example.v1.migrations.DataBaseMigrationTags
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.api.builder.migration

class CreateCustomersEmailIndexMigrationV1 : Migration(DataBaseMigrationTags.Items.CREATE_INDEX) {
    override fun up(): MigrationDefinition = migration {
        operation(Schema.createIndex {
            tableName("customers")
            name("idx_customers_email")
            column("email")
        })
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropIndex {
            tableName("customers")
            name("idx_customers_email")
        })
    }
}
