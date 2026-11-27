package gog.my_project.data_base_migration.core.system_migration.schema

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.builder.metadata.models.ModelMetadataResolver
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base.dialect.DatabaseDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemMigrationVersionTableTest {
    @Test
    fun `status column is integer not null with migrate started default`() {
        val sql = Schema.render(SystemMigrationVersionTable.definition(), DatabaseDialect.MYSQL)

        assertTrue(sql.contains("`status` INT NOT NULL DEFAULT 100"), sql)
    }

    @Test
    fun `message column is nullable text without a default`() {
        val sql = Schema.render(SystemMigrationVersionTable.definition(), DatabaseDialect.MYSQL)

        assertTrue(sql.contains("`message` TEXT NULL"), sql)
        assertFalse(sql.contains("`message` TEXT NOT NULL"), sql)
        assertFalse(sql.contains("`message` TEXT NULL DEFAULT"), sql)
    }

    @Test
    fun `retry checkpoint column is nullable text without a default`() {
        val sql = Schema.render(SystemMigrationVersionTable.definition(), DatabaseDialect.MYSQL)

        assertTrue(sql.contains("`retry_checkpoint` TEXT NULL"), sql)
        assertFalse(sql.contains("`retry_checkpoint` TEXT NOT NULL"), sql)
        assertFalse(sql.contains("`retry_checkpoint` TEXT NULL DEFAULT"), sql)
    }

    @Test
    fun `status and message model properties map their schema columns and expose status constants`() {
        val property = SystemMigrationVersionModel::status
        val model = SystemMigrationVersionModel()
        val messageProperty = SystemMigrationVersionModel::message
        val checkpointProperty = SystemMigrationVersionModel::retryCheckpoint

        assertEquals("status", ModelMetadataResolver.columnName(SystemMigrationVersionModel::class, property))
        assertEquals("system_migration_version_status", ModelMetadataResolver.columnAlias(property))
        assertEquals(SystemMigrationVersionModel.STATUS_MIGRATE_STARTED, model.status)
        assertEquals("message", ModelMetadataResolver.columnName(SystemMigrationVersionModel::class, messageProperty))
        assertEquals("system_migration_version_message", ModelMetadataResolver.columnAlias(messageProperty))
        assertEquals(null, model.message)
        assertEquals("retry_checkpoint", ModelMetadataResolver.columnName(SystemMigrationVersionModel::class, checkpointProperty))
        assertEquals("system_migration_version_retry_checkpoint", ModelMetadataResolver.columnAlias(checkpointProperty))
        assertEquals(null, model.retryCheckpoint)

        assertEquals(100, SystemMigrationVersionModel.STATUS_MIGRATE_STARTED)
        assertEquals(101, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_UP)
        assertEquals(102, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD)
        assertEquals(103, SystemMigrationVersionModel.STATUS_MIGRATE_COMPLETED)
        assertEquals(110, SystemMigrationVersionModel.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED)
        assertEquals(200, SystemMigrationVersionModel.STATUS_ROLLBACK_STARTED)
        assertEquals(201, SystemMigrationVersionModel.STATUS_ROLLBACK_FAILED_DOWN)
        assertEquals(202, SystemMigrationVersionModel.STATUS_ROLLBACK_FAILED_RECORD)
        assertEquals(210, SystemMigrationVersionModel.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED)
    }
}
