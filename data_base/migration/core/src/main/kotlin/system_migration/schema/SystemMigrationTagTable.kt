package gog.my_project.data_base_migration.core.system_migration.schema

import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDataType
import gog.my_project.data_base_schema.SchemaOperation

/** Schema definition for the shared migration tag registry. */
object SystemMigrationTagTable {
    const val NAME = "system_migration_tag"
    const val UNIQUE_NAME_TYPE_INDEX = "uq_system_migration_tag_name_type"

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
            name("tagName")
            dataType(SchemaDataType.VarcharType(255))
            notNull()
        }
        addColumn {
            name("tagType")
            dataType(SchemaDataType.IntType)
            notNull()
        }
        addColumn {
            name("created_at")
            dataType(SchemaDataType.TimestampType())
            nullable()
        }

        unique {
            name(UNIQUE_NAME_TYPE_INDEX)
            columns("tagName", "tagType")
        }
    }
}
