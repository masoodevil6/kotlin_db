package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryOperation
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryPhase
import model.version_history.MigrationHistoryVersion

/** Validates a caller-supplied selector against a freshly resolved locked database snapshot. */
internal class MigrationRetryTargetValidator(
    private val diagnosticValidator: MigrationRetryDiagnosticValidator = MigrationRetryDiagnosticValidator(),
) {
    fun validate(
        supplied: MigrationRecoveryTarget,
        current: MigrationRecoveryTarget,
        row: MigrationHistoryVersion,
        assessment: MigrationCheckpointStatusAssessment,
        configuredVersionResolved: Boolean,
        registeredMigrations: List<RegisteredMigration>,
    ): MigrationRecoveryTarget {
        require(supplied.checkpointRevision != null) {
            "Retry Target has no Checkpoint revision and cannot establish freshness"
        }
        val checkpoint = requireNotNull(assessment.checkpoint) {
            "Retry requires a valid persisted Checkpoint"
        }

        require(row.id > 0 && row.id == supplied.version.versionId) {
            "Retry Target Version ID does not match the persisted Version"
        }
        require(row.tagId > 0 && row.tagId == supplied.version.tagId) {
            "Retry Target Tag ID does not match the persisted Version"
        }
        require(current.version == supplied.version) {
            "Retry Target Version snapshot is stale"
        }
        require(current.checkpointRevision == checkpoint.revision &&
            supplied.checkpointRevision == checkpoint.revision
        ) { "Retry Target Checkpoint revision is stale" }
        require(current.operation == supplied.operation) {
            "Retry Target operation or direction is stale"
        }
        require(current.migration == supplied.migration) {
            "Retry Target Migration registration is stale"
        }
        require(configuredVersionResolved) {
            "Persisted Version Tag does not resolve to one current Configuration registration"
        }

        val registeredIds = registeredMigrations.mapTo(mutableSetOf()) { it.id.value }
        when {
            checkpoint.cursorMigrationId != null -> {
                require(checkpoint.phase != MigrationRetryPhase.FINALIZATION_PENDING) {
                    "Finalization Checkpoint unexpectedly has a Migration cursor"
                }
                require(checkpoint.cursorMigrationId in registeredIds) {
                    "Checkpoint cursor is not registered in the current Version"
                }
                require(current.migration?.id?.value == checkpoint.cursorMigrationId) {
                    "Resolved Migration does not match the persisted Checkpoint cursor"
                }
            }
            else -> {
                require(checkpoint.phase == MigrationRetryPhase.FINALIZATION_PENDING) {
                    "Only FINALIZATION_PENDING may omit the Migration cursor"
                }
                require(current.migration == null) {
                    "Version-level finalization Target must not identify a Migration"
                }
            }
        }

        require(isRetryableStatusPhase(row.status, assessment.kind)) {
            "Status ${row.status} and Checkpoint phase ${checkpoint.phase} are not eligible for automatic Retry"
        }
        require(supplied.operation != MigrationRecoveryOperation.UNKNOWN &&
            supplied.operation != MigrationRecoveryOperation.UP_MANUAL_RECOVERY_REQUIRED &&
            supplied.operation != MigrationRecoveryOperation.DOWN_MANUAL_RECOVERY_REQUIRED
        ) { "Unknown and manual-recovery Targets cannot be retried automatically" }

        if (row.status == MigrationVersionState.STATUS_MIGRATE_FAILED_UP ||
            row.status == MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN
        ) {
            diagnosticValidator.validateExecutionFailure(
                status = row.status,
                versionId = row.id,
                checkpoint = checkpoint,
                currentMessage = row.message,
                registeredMigrationIds = registeredIds,
            )
        }

        if (row.status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD ||
            row.status == MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
        ) {
            diagnosticValidator.validateRecordingFailure(
                status = row.status,
                versionId = row.id,
                checkpoint = checkpoint,
                currentMessage = row.message,
                registeredMigrationIds = registeredIds,
            )
        }

        // Return the fresh registration, never the caller's object, for any later dispatch.
        return current
    }

    private fun isRetryableStatusPhase(
        status: Int,
        kind: MigrationCheckpointStatusKind,
    ): Boolean = when (status) {
        MigrationVersionState.STATUS_MIGRATE_STARTED ->
            kind == MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE ||
                kind == MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE
        MigrationVersionState.STATUS_ROLLBACK_STARTED ->
            kind == MigrationCheckpointStatusKind.DOWN_EXECUTION_RETRYABLE ||
                kind == MigrationCheckpointStatusKind.DOWN_RECORDING_RETRYABLE
        MigrationVersionState.STATUS_MIGRATE_FAILED_UP ->
            kind == MigrationCheckpointStatusKind.UP_EXECUTION_FAILURE_NOT_RETRYABLE
        MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN ->
            kind == MigrationCheckpointStatusKind.DOWN_EXECUTION_FAILURE_NOT_RETRYABLE
        MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD ->
            kind == MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE
        MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD ->
            kind == MigrationCheckpointStatusKind.DOWN_RECORDING_RETRYABLE
        else -> false
    }
}
