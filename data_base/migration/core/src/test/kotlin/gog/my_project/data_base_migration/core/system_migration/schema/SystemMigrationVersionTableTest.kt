package gog.my_project.data_base_migration.core.system_migration.schema

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.builder.metadata.models.ModelMetadataResolver
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemMigrationVersionTableTest {
    @Test
    fun `status column is integer not null with zero default`() {
        val sql = Schema.render(SystemMigrationVersionTable.definition(), SchemaDialect.MYSQL)

        assertTrue(sql.contains("`status` INT NOT NULL DEFAULT 0"), sql)
    }

    @Test
    fun `message column is nullable text without a default`() {
        val sql = Schema.render(SystemMigrationVersionTable.definition(), SchemaDialect.MYSQL)

        assertTrue(sql.contains("`message` TEXT NULL"), sql)
        assertFalse(sql.contains("`message` TEXT NOT NULL"), sql)
        assertFalse(sql.contains("`message` TEXT NULL DEFAULT"), sql)
    }

    @Test
    fun `status and message model properties map their schema columns and expose status constants`() {
        val property = SystemMigrationVersionModel::status
        val model = SystemMigrationVersionModel()
        val messageProperty = SystemMigrationVersionModel::message

        assertEquals("status", ModelMetadataResolver.columnName(SystemMigrationVersionModel::class, property))
        assertEquals("system_migration_version_status", ModelMetadataResolver.columnAlias(property))
        assertEquals(SystemMigrationVersionModel.STATUS_MIGRATE_STARTED, model.status)
        assertEquals("message", ModelMetadataResolver.columnName(SystemMigrationVersionModel::class, messageProperty))
        assertEquals("system_migration_version_message", ModelMetadataResolver.columnAlias(messageProperty))
        assertEquals(null, model.message)

        assertEquals(0, SystemMigrationVersionModel.STATUS_MIGRATE_STARTED)
        assertEquals(1, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_UP)
        assertEquals(2, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD)
        assertEquals(3, SystemMigrationVersionModel.STATUS_MIGRATE_COMPLETED)
        assertEquals(4, SystemMigrationVersionModel.STATUS_ROLLBACK_STARTED)
        assertEquals(5, SystemMigrationVersionModel.STATUS_ROLLBACK_FAILED_DOWN)
        assertEquals(6, SystemMigrationVersionModel.STATUS_ROLLBACK_FAILED_RECORD)
    }
}
