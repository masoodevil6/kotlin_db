package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.params.data_types.IntType
import gog.my_project.data_base.migration.params.data_types.VarcharType

/** Defines the internal system_migration table schema; it does not execute the DDL. */
internal object SystemMigrationTable {
    const val NAME = "system_migration"
    private const val MIGRATION_ID_LENGTH = 255

    fun definition() = createTable {
        table { tableName(NAME) }
        addColumn {
            name("id")
            dataType(IntType())
            notNull()
            autoIncrement()
            primaryKey()
        }
        addColumn {
            name("migration")
            dataType(VarcharType(MIGRATION_ID_LENGTH))
            notNull()
        }
        addColumn {
            name("batch")
            dataType(IntType())
            notNull()
        }
        unique {
            name("uq_system_migration_migration")
            columns("migration")
        }
        ifNotExists()
    }
}
