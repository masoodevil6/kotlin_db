package gog.my_project.example.migrations

import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base.core.annotations.migrations.MigrationId
import gog.my_project.data_base_migration.api.builder.migration
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.example.DataBaseMigrationTags

//@MigrationId(DataBaseMigrationTags.Items.MANAGER_CUSTOMER)
class CreateManagerCustomers : Migration(
    DataBaseMigrationTags.Items.MANAGER_CUSTOMER
) {
    override fun up(): MigrationDefinition = migration {
        operation(
            Schema.createTable {
                table { tableName("manager_customers") }
                addColumn {
                    name("id")
                    dataType(SchemaDataType.IntType)
                    notNull()
                    autoIncrement()
                    primaryKey()
                }
                addColumn {
                    name("display_name")
                    dataType(SchemaDataType.VarcharType(120))
                    notNull()
                }
            }
        )
    }

    override fun down(): MigrationDefinition = migration {
        operation(Schema.dropTable { tableName("manager_customers") })
    }
}
