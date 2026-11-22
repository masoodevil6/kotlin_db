package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase

internal enum class MigrationCheckpointStatusKind {
    COMPLETED,
    UP_EXECUTION_RETRYABLE,
    UP_RECORDING_RETRYABLE,
    DOWN_EXECUTION_RETRYABLE,
    DOWN_RECORDING_RETRYABLE,
    UP_EXECUTION_FAILURE_NOT_RETRYABLE,
    DOWN_EXECUTION_FAILURE_NOT_RETRYABLE,
    UP_MANUAL_RECOVERY,
    DOWN_MANUAL_RECOVERY,
    INVALID,
}

internal data class MigrationCheckpointStatusAssessment(
    val kind: MigrationCheckpointStatusKind,
    val checkpoint: MigrationRetryCheckpoint? = null,
    val reason: String? = null,
)

/** Shared Status/Checkpoint compatibility matrix; all execution and reporting consumers use this classifier. */
internal class MigrationCheckpointStatusValidator {
    fun assess(status: Int, rawCheckpoint: String?): MigrationCheckpointStatusAssessment {
        if (status == MigrationVersionState.STATUS_MIGRATE_COMPLETED) {
            return if (rawCheckpoint == null) {
                MigrationCheckpointStatusAssessment(MigrationCheckpointStatusKind.COMPLETED)
            } else {
                invalid("Completed Version must not retain a Retry Checkpoint")
            }
        }

        if (status == MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED) {
            return manual(MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY, rawCheckpoint, expectedDirection = 100)
        }
        if (status == MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED) {
            return manual(MigrationCheckpointStatusKind.DOWN_MANUAL_RECOVERY, rawCheckpoint, expectedDirection = 200)
        }

        val checkpoint = if (rawCheckpoint == null) {
            null
        } else {
            try {
                MigrationRetryCheckpointCodec.decode(rawCheckpoint)
            } catch (failure: Exception) {
                return invalid("Retry Checkpoint is malformed or unsupported: ${failure.message}")
            }
        }

        return when (status) {
            MigrationVersionState.STATUS_MIGRATE_STARTED -> assessStarted(
                checkpoint,
                expectedDirection = MigrationVersionState.STATUS_MIGRATE_STARTED,
                up = true,
            )
            MigrationVersionState.STATUS_ROLLBACK_STARTED -> assessStarted(
                checkpoint,
                expectedDirection = MigrationVersionState.STATUS_ROLLBACK_STARTED,
                up = false,
            )
            MigrationVersionState.STATUS_MIGRATE_FAILED_UP -> assessLegacyExecutionFailure(checkpoint, up = true)
            MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN -> assessLegacyExecutionFailure(checkpoint, up = false)
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD -> assessRecordingFailure(checkpoint, up = true)
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD -> assessRecordingFailure(checkpoint, up = false)
            else -> invalid("Unknown Migration Version status $status")
        }
    }

    private fun assessStarted(
        checkpoint: MigrationRetryCheckpoint?,
        expectedDirection: Int,
        up: Boolean,
    ): MigrationCheckpointStatusAssessment {
        val checked = requireDirection(checkpoint, expectedDirection) ?: return invalid(
            "Started Version requires a valid ${directionName(up)} Retry Checkpoint",
        )
        return when (checked.phase) {
            MigrationRetryPhase.EXECUTION_IN_FLIGHT -> manualKind(up, checked)
            MigrationRetryPhase.EXECUTION_PENDING,
            MigrationRetryPhase.PAUSED_NEXT,
            -> retryKind(up, recording = false, checked)
            MigrationRetryPhase.RECORD_PENDING,
            MigrationRetryPhase.FINALIZATION_PENDING,
            -> retryKind(up, recording = true, checked)
        }
    }

    private fun assessRecordingFailure(
        checkpoint: MigrationRetryCheckpoint?,
        up: Boolean,
    ): MigrationCheckpointStatusAssessment {
        val expectedDirection = if (up) {
            MigrationVersionState.STATUS_MIGRATE_STARTED
        } else {
            MigrationVersionState.STATUS_ROLLBACK_STARTED
        }
        val checked = requireDirection(checkpoint, expectedDirection) ?: return invalid(
            "Recording failure requires a valid ${directionName(up)} Retry Checkpoint",
        )
        if (checked.originalMessage == null) {
            return invalid("Recording failure Checkpoint must preserve the first Diagnostic")
        }
        if (checked.phase != MigrationRetryPhase.RECORD_PENDING &&
            checked.phase != MigrationRetryPhase.FINALIZATION_PENDING
        ) {
            return invalid("Recording failure is compatible only with RECORD_PENDING or FINALIZATION_PENDING")
        }
        return retryKind(up, recording = true, checked)
    }

    private fun assessLegacyExecutionFailure(
        checkpoint: MigrationRetryCheckpoint?,
        up: Boolean,
    ): MigrationCheckpointStatusAssessment {
        if (checkpoint != null) {
            val expectedDirection = if (up) {
                MigrationVersionState.STATUS_MIGRATE_STARTED
            } else {
                MigrationVersionState.STATUS_ROLLBACK_STARTED
            }
            if (checkpoint.originalStatus != expectedDirection) {
                return invalid("Execution failure Status and Retry Checkpoint directions disagree")
            }
        }
        return MigrationCheckpointStatusAssessment(
            if (up) MigrationCheckpointStatusKind.UP_EXECUTION_FAILURE_NOT_RETRYABLE
            else MigrationCheckpointStatusKind.DOWN_EXECUTION_FAILURE_NOT_RETRYABLE,
            checkpoint,
            "Execution failure does not prove that Schema had no effects",
        )
    }

    private fun manual(
        kind: MigrationCheckpointStatusKind,
        rawCheckpoint: String?,
        expectedDirection: Int,
    ): MigrationCheckpointStatusAssessment {
        if (rawCheckpoint == null) return MigrationCheckpointStatusAssessment(kind)
        val checkpoint = try {
            MigrationRetryCheckpointCodec.decode(rawCheckpoint)
        } catch (failure: Exception) {
            return MigrationCheckpointStatusAssessment(kind, reason = "Manual status has malformed Checkpoint: ${failure.message}")
        }
        return if (checkpoint.originalStatus == expectedDirection) {
            MigrationCheckpointStatusAssessment(kind, checkpoint)
        } else {
            MigrationCheckpointStatusAssessment(
                kind,
                reason = "Manual Status and Checkpoint directions disagree",
            )
        }
    }

    private fun requireDirection(
        checkpoint: MigrationRetryCheckpoint?,
        expectedDirection: Int,
    ): MigrationRetryCheckpoint? = checkpoint?.takeIf { it.originalStatus == expectedDirection }

    private fun retryKind(
        up: Boolean,
        recording: Boolean,
        checkpoint: MigrationRetryCheckpoint,
    ) = MigrationCheckpointStatusAssessment(
        when {
            up && recording -> MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE
            up -> MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE
            recording -> MigrationCheckpointStatusKind.DOWN_RECORDING_RETRYABLE
            else -> MigrationCheckpointStatusKind.DOWN_EXECUTION_RETRYABLE
        },
        checkpoint,
    )

    private fun manualKind(up: Boolean, checkpoint: MigrationRetryCheckpoint) = MigrationCheckpointStatusAssessment(
        if (up) MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY
        else MigrationCheckpointStatusKind.DOWN_MANUAL_RECOVERY,
        checkpoint,
        "EXECUTION_IN_FLIGHT cannot be resumed automatically",
    )

    private fun directionName(up: Boolean) = if (up) "UP" else "DOWN"

    private fun invalid(reason: String) = MigrationCheckpointStatusAssessment(
        MigrationCheckpointStatusKind.INVALID,
        reason = reason,
    )
}
