package gog.my_project.data_base_migration.core.system_migration.schema

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaOperation

/** Schema definition for migration versions, each linked to its version tag. */
object SystemMigrationVersionTable {
    const val NAME = "system_migration_version"

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
            name("created_at")
            dataType(SchemaDataType.TimestampType())
            nullable()
        }

        foreignKey {
            name("fk_system_migration_version_tag")
            columns("tag_id")
            referencesTable(SystemMigrationTagTable.NAME)
            referencesColumns("id")
        }
    }
}
