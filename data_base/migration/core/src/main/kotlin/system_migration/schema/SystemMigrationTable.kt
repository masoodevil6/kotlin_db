package gog.my_project.data_base_migration.core.system_migration.schema

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaOperation

/** Schema definition for applied migrations and their owning version. */
object SystemMigrationTable {
    const val NAME = "system_migration"

    fun definition(): SchemaOperation = Schema.createTable {
        table { tableName(NAME) }
        ifNotExists()

        addColumn {
            name("id")
            dataType(SchemaDataType.IntType)
            notNull()
            autoIncrement()
            primaryKey()
        }
        addColumn {
            name("tag_id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
        addColumn {
            name("version_id")
            dataType(SchemaDataType.IntType)
            notNull()
        }
        addColumn {
            name("created_at")
            dataType(SchemaDataType.TimestampType())
            nullable()
        }

        foreignKey {
            name("fk_system_migration_tag")
            columns("tag_id")
            referencesTable(SystemMigrationTagTable.NAME)
            referencesColumns("id")
        }
        foreignKey {
            name("fk_system_migration_version")
            columns("version_id")
            referencesTable(SystemMigrationVersionTable.NAME)
            referencesColumns("id")
        }
    }
}
