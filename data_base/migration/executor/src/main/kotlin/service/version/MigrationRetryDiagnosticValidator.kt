package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnostic
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnosticStage
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase

/** Strictly validates the persisted diagnostic pair needed to resume a recording-only failure. */
internal class MigrationRetryDiagnosticValidator {
    fun validateRecordingFailure(
        status: Int,
        versionId: Int,
        checkpoint: MigrationRetryCheckpoint,
        currentMessage: String?,
        registeredMigrationIds: Set<String>,
    ) {
        val (up, expectedStage) = when (status) {
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD ->
                true to MigrationFailureDiagnosticStage.UP_RECORDING
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD ->
                false to MigrationFailureDiagnosticStage.DOWN_RECORDING
            else -> throw IllegalArgumentException("Status $status is not a recording failure")
        }

        val expectedOriginalStatus = if (up) {
            MigrationVersionState.STATUS_MIGRATE_STARTED
        } else {
            MigrationVersionState.STATUS_ROLLBACK_STARTED
        }
        require(checkpoint.originalStatus == expectedOriginalStatus) {
            "Recording Failure Status and Checkpoint directions disagree"
        }
        require(
            checkpoint.phase == MigrationRetryPhase.RECORD_PENDING ||
                checkpoint.phase == MigrationRetryPhase.FINALIZATION_PENDING,
        ) { "Recording Failure is valid only with a recording or finalization Checkpoint" }

        when (checkpoint.phase) {
            MigrationRetryPhase.RECORD_PENDING -> {
                val cursor = requireNotNull(checkpoint.cursorMigrationId) {
                    "RECORD_PENDING requires a Migration cursor"
                }
                require(cursor in registeredMigrationIds) {
                    "Checkpoint cursor '$cursor' is not registered for Version $versionId"
                }
            }
            MigrationRetryPhase.FINALIZATION_PENDING -> require(checkpoint.cursorMigrationId == null) {
                "FINALIZATION_PENDING must not have a Migration cursor"
            }
            else -> error("Phase validation above should exclude ${checkpoint.phase}")
        }

        val current = parse(
            currentMessage,
            expectedVersionId = versionId,
            expectedStage = expectedStage,
            registeredMigrationIds = registeredMigrationIds,
        )
        if (checkpoint.phase == MigrationRetryPhase.FINALIZATION_PENDING) {
            require(current.migrationId == null) {
                "Finalization Diagnostic must not identify an individual Migration"
            }
        }

        // The first Diagnostic can belong to an earlier Migration than the current cursor.
        // Membership in the current Version is the safe check; cursor equality is not required.
        parse(
            checkpoint.originalMessage,
            expectedVersionId = versionId,
            expectedStage = expectedStage,
            registeredMigrationIds = registeredMigrationIds,
        )
    }

    private fun parse(
        raw: String?,
        expectedVersionId: Int,
        expectedStage: MigrationFailureDiagnosticStage,
        registeredMigrationIds: Set<String>,
    ): ParsedDiagnostic {
        requireNotNull(raw) { "Recording Failure Diagnostic is missing" }
        require(raw.codePointCount(0, raw.length) <= MigrationFailureDiagnostic.MAX_STORAGE_MESSAGE_LENGTH) {
            "Recording Failure Diagnostic exceeds the storage limit"
        }

        val fields = try {
            MigrationRetryCheckpointCodec.StrictObjectParser(raw).parse()
        } catch (failure: Exception) {
            throw IllegalArgumentException("Recording Failure Diagnostic is malformed", failure)
        }
        require(fields.keys == REQUIRED_FIELDS) {
            "Recording Failure Diagnostic must contain exactly the supported fields"
        }

        val schemaVersion = diagnosticField("schemaVersion") { fields.getValue("schemaVersion").intValue("schemaVersion") }
        require(schemaVersion == MigrationFailureDiagnostic.SCHEMA_VERSION) {
            "Unsupported Migration Failure Diagnostic schema version $schemaVersion"
        }
        val stageName = diagnosticField("stage") { fields.getValue("stage").stringValue("stage") }
        val stage = MigrationFailureDiagnosticStage.entries.singleOrNull { it.name == stageName }
            ?: throw IllegalArgumentException("Unknown Migration Failure Diagnostic stage '$stageName'")
        require(stage == expectedStage) { "Diagnostic stage $stage does not match the persisted Status" }

        val diagnosticVersionId = diagnosticField("versionId") {
            fields.getValue("versionId").intValue("versionId")
        }
        require(diagnosticVersionId > 0 && diagnosticVersionId == expectedVersionId) {
            "Diagnostic Version ID does not match the persisted Version"
        }

        val migrationId = diagnosticField("migrationId") {
            fields.getValue("migrationId").nullableStringValue("migrationId")
        }
        if (migrationId != null) {
            val validIdentity = try {
                MigrationIdentity.fromValue(migrationId).value == migrationId
            } catch (_: IllegalArgumentException) {
                false
            }
            require(validIdentity) { "Diagnostic Migration ID is invalid" }
            require(migrationId in registeredMigrationIds) {
                "Diagnostic Migration ID '$migrationId' is not registered for Version $expectedVersionId"
            }
        }

        val exception = diagnosticField("exception") { fields.getValue("exception").stringValue("exception") }
        require(EXCEPTION_CLASS.matches(exception)) { "Diagnostic exception class is invalid" }

        val sqlState = diagnosticField("sqlState") {
            fields.getValue("sqlState").nullableStringValue("sqlState")
        }
        require(sqlState == null || SQL_STATE.matches(sqlState)) { "Diagnostic SQLState is invalid" }

        diagnosticField("errorCode") {
            val value = fields.getValue("errorCode")
            if (value.source == "null") null else value.intValue("errorCode")
        }
        diagnosticField("message") { fields.getValue("message").stringValue("message") }

        return ParsedDiagnostic(stage, diagnosticVersionId, migrationId)
    }

    private inline fun <T> diagnosticField(name: String, read: () -> T): T = try {
        read()
    } catch (failure: Exception) {
        throw IllegalArgumentException("Diagnostic field '$name' has an invalid or missing value", failure)
    }

    private data class ParsedDiagnostic(
        val stage: MigrationFailureDiagnosticStage,
        val versionId: Int,
        val migrationId: String?,
    )

    private companion object {
        val REQUIRED_FIELDS = setOf(
            "schemaVersion",
            "stage",
            "versionId",
            "migrationId",
            "exception",
            "sqlState",
            "errorCode",
            "message",
        )
        val EXCEPTION_CLASS = Regex("""[A-Za-z_$][A-Za-z0-9_.$]*""")
        val SQL_STATE = Regex("""[A-Za-z0-9_-]{1,16}""")
    }
}
