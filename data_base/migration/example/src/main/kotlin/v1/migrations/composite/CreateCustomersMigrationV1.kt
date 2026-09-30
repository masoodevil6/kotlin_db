package gog.my_project.data_base.migration.example.v1.migrations.composite

import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.VarcharType

class CreateCustomersMigrationV1 : Migration {
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
            addColumn {
                name("email")
                dataType(VarcharType(255))
                notNull()
            }
        }
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
        dropTable {
            tableName("customers")
        }
    }
}
