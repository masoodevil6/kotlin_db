package gog.my_project.data_base_operation.migration.failure.spi

/** One of the fixed Migration failure transitions supported by the Operation capability. */
public sealed interface MigrationFailureStatusCommand {
    public val versionId: Int

    public data class UpExecutionFailure(
        override val versionId: Int,
    ) : MigrationFailureStatusCommand

    public data class UpRecordingFailure(
        override val versionId: Int,
    ) : MigrationFailureStatusCommand

    public data class DownExecutionFailure(
        override val versionId: Int,
    ) : MigrationFailureStatusCommand

    public data class DownRecordingFailure(
        override val versionId: Int,
    ) : MigrationFailureStatusCommand
}

/** Result of one conditional Migration failure status write. */
public enum class MigrationFailureStatusWriteResult {
    APPLIED,
    NOT_APPLIED,
    INDETERMINATE,
}

/** Narrow callback-scoped capability for the four fixed Migration failure status writes. */
public interface MigrationFailureStatusCapability {
    public fun write(
        command: MigrationFailureStatusCommand,
    ): MigrationFailureStatusWriteResult
}

/** Closed failure writes that atomically replace Status, latest Diagnostic, and Retry Checkpoint. */
public sealed interface MigrationFailureCheckpointWriteCommand {
    public val versionId: Int
    public val expectedCheckpoint: String
    public val updatedCheckpoint: String
    public val diagnosticMessage: String

    public data class UpExecutionUncertain(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class UpRecordingFailure(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class UpReconciliationInconsistent(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class UpFinalizationFailure(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class DownExecutionUncertain(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class DownRecordingFailure(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class DownReconciliationInconsistent(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand

    public data class DownFinalizationFailure(
        override val versionId: Int,
        override val expectedCheckpoint: String,
        override val updatedCheckpoint: String,
        override val diagnosticMessage: String,
    ) : MigrationFailureCheckpointWriteCommand
}

/** Additive, bounded Checkpoint write available only while the existing Failure capability lease is active. */
public interface MigrationFailureCheckpointWriteCapability : MigrationFailureStatusCapability {
    public fun writeCheckpoint(
        command: MigrationFailureCheckpointWriteCommand,
    ): MigrationFailureStatusWriteResult
}

/**
 * Additive diagnostic-aware form implemented by managed Operation scopes. Existing capability
 * implementations can keep implementing [MigrationFailureStatusCapability] unchanged.
 */
public interface MigrationFailureDiagnosticStatusCapability : MigrationFailureStatusCapability {
    public fun write(
        command: MigrationFailureStatusCommand,
        diagnostic: MigrationFailureDiagnostic,
    ): MigrationFailureStatusWriteResult
}

/**
 * Writes a diagnostic when the capability supports it. Older capability implementations retain
 * their original status-only behavior.
 */
public fun MigrationFailureStatusCapability.write(
    command: MigrationFailureStatusCommand,
    diagnostic: MigrationFailureDiagnostic,
): MigrationFailureStatusWriteResult =
    if (this is MigrationFailureDiagnosticStatusCapability) {
        write(command, diagnostic)
    } else {
        write(command)
    }
