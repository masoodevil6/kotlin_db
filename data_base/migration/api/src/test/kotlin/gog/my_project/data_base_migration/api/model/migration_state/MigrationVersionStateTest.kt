package gog.my_project.data_base_migration.api.model.migration_state

import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationVersionStateTest {
    @Test
    fun `public persisted status constants use the migrate and rollback numeric ranges`() {
        assertEquals(100, MigrationVersionState.STATUS_MIGRATE_STARTED)
        assertEquals(101, MigrationVersionState.STATUS_MIGRATE_FAILED_UP)
        assertEquals(102, MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD)
        assertEquals(103, MigrationVersionState.STATUS_MIGRATE_COMPLETED)
        assertEquals(110, MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED)
        assertEquals(200, MigrationVersionState.STATUS_ROLLBACK_STARTED)
        assertEquals(201, MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN)
        assertEquals(202, MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD)
        assertEquals(210, MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED)
    }
}
