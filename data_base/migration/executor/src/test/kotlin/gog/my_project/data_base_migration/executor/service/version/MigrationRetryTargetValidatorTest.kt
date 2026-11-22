package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import model.version_history.MigrationHistoryVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MigrationRetryTargetValidatorTest {
    private val validator = MigrationRetryTargetValidator()
    private val alpha = RegisteredMigration(MigrationIdentity.fromValue("alpha"), Migration::class)
    private val beta = RegisteredMigration(MigrationIdentity.fromValue("beta"), Migration::class)
    private val registrations = listOf(alpha, beta)

    @Test
    fun `accepts only the current reloaded version and checkpoint revision`() {
        val resolved = resolvedTarget()

        assertEquals(
            resolved.target,
            validator.validate(
                supplied = resolved.target,
                current = resolved.target,
                row = resolved.row,
                assessment = resolved.assessment,
                configuredVersionResolved = true,
                registeredMigrations = registrations,
            ),
        )
    }

    @Test
    fun `rejects targets without revision and targets from an older checkpoint revision`() {
        val resolved = resolvedTarget()
        assertRejected(resolved, target(revision = null))
        assertRejected(resolved, target(revision = 4))
    }

    @Test
    fun `rejects stale version tag status message operation and migration snapshots`() {
        val resolved = resolvedTarget()
        assertRejected(resolved, target(state = resolved.row.toState().copy(versionId = VERSION_ID + 1)))
        assertRejected(resolved, target(state = resolved.row.toState().copy(tagId = TAG_ID + 1)))
        assertRejected(resolved, target(state = resolved.row.toState().copy(status = MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD)))
        assertRejected(resolved, target(state = resolved.row.toState().copy(message = "changed")))
        assertRejected(resolved, target(operation = MigrationRecoveryOperation.DOWN_EXECUTION))
        assertRejected(resolved, target(migration = beta))
    }

    @Test
    fun `rejects unresolved configuration registrations and cursor outside the version`() {
        val resolved = resolvedTarget()
        assertFailsWith<IllegalArgumentException> {
            validator.validate(
                supplied = resolved.target,
                current = resolved.target,
                row = resolved.row,
                assessment = resolved.assessment,
                configuredVersionResolved = false,
                registeredMigrations = registrations,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            validator.validate(
                supplied = resolved.target,
                current = resolved.target,
                row = resolved.row,
                assessment = resolved.assessment,
                configuredVersionResolved = true,
                registeredMigrations = listOf(beta),
            )
        }
    }

    private fun assertRejected(resolved: Resolved, supplied: MigrationRecoveryTarget) {
        assertFailsWith<IllegalArgumentException> {
            validator.validate(
                supplied = supplied,
                current = resolved.target,
                row = resolved.row,
                assessment = resolved.assessment,
                configuredVersionResolved = true,
                registeredMigrations = registrations,
            )
        }
    }

    private fun resolvedTarget(): Resolved {
        val checkpoint = MigrationRetryCheckpoint(
            formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
            revision = 5,
            originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
            originalMessage = null,
            cursorMigrationId = "alpha",
            phase = MigrationRetryPhase.EXECUTION_PENDING,
        )
        val encoded = MigrationRetryCheckpointCodec.encode(checkpoint)
        val row = MigrationHistoryVersion(VERSION_ID, TAG_ID, MigrationVersionState.STATUS_MIGRATE_STARTED, null, encoded)
        val assessment = MigrationCheckpointStatusValidator().assess(row.status, row.retryCheckpoint)
        val state = row.toState()
        val target = MigrationRecoveryTarget(
            state,
            alpha,
            MigrationRecoveryOperation.START_EXECUTION,
            checkpoint.revision,
        )
        return Resolved(row, assessment, target)
    }

    private fun MigrationHistoryVersion.toState() = MigrationVersionState(id, tagId, status, message)

    private fun target(
        state: MigrationVersionState = MigrationVersionState(
            VERSION_ID,
            TAG_ID,
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            null,
        ),
        migration: RegisteredMigration? = alpha,
        operation: MigrationRecoveryOperation = MigrationRecoveryOperation.START_EXECUTION,
        revision: Long? = 5,
    ) = MigrationRecoveryTarget(state, migration, operation, revision)

    private data class Resolved(
        val row: MigrationHistoryVersion,
        val assessment: MigrationCheckpointStatusAssessment,
        val target: MigrationRecoveryTarget,
    )

    private companion object {
        const val VERSION_ID = 41
        const val TAG_ID = 12
    }
}
