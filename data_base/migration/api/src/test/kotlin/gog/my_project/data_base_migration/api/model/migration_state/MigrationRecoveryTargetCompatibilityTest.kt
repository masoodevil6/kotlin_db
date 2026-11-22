package gog.my_project.data_base_migration.api.model.migration_state

import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class MigrationRecoveryTargetCompatibilityTest {
    @Test
    fun `preserves the original constructor components and copy contract`() {
        val state = MigrationVersionState(41, 12, MigrationVersionState.STATUS_MIGRATE_STARTED, null)
        val target = MigrationRecoveryTarget(state, null, MigrationRecoveryOperation.START_EXECUTION)

        assertEquals(state, target.component1())
        assertNull(target.component2())
        assertEquals(MigrationRecoveryOperation.START_EXECUTION, target.component3())
        assertNull(target.checkpointRevision)

        val copied = target.copy(version = state.copy(tagId = 13))
        assertEquals(13, copied.version.tagId)
        assertNull(copied.checkpointRevision)
    }

    @Test
    fun `revision constructor is additive and revision does not participate in data class copy`() {
        val state = MigrationVersionState(41, 12, MigrationVersionState.STATUS_MIGRATE_STARTED, null)
        val target = MigrationRecoveryTarget(
            state,
            null,
            MigrationRecoveryOperation.START_EXECUTION,
            checkpointRevision = 7,
        )

        assertEquals(7L, target.checkpointRevision)
        assertEquals(7L, target.checkpointRevision)
        assertNull(target.copy().checkpointRevision)
    }

    @Test
    fun `the original three argument JVM constructor remains available to Java callers`() {
        val constructor = MigrationRecoveryTarget::class.java.getConstructor(
            MigrationVersionState::class.java,
            RegisteredMigration::class.java,
            MigrationRecoveryOperation::class.java,
        )
        val target = constructor.newInstance(
            MigrationVersionState(41, 12, MigrationVersionState.STATUS_MIGRATE_STARTED, null),
            null,
            MigrationRecoveryOperation.START_EXECUTION,
        )

        assertNotNull(target)
        assertNull(target.checkpointRevision)
    }
}
