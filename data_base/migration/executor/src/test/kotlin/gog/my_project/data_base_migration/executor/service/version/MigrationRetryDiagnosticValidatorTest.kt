package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnostic
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnosticStage
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MigrationRetryDiagnosticValidatorTest {
    private val validator = MigrationRetryDiagnosticValidator()
    private val registeredIds = setOf("alpha", "beta")

    @Test
    fun `accepts execution failure diagnostics only for the exact in-flight cursor`() {
        for ((status, stage, originalStatus) in listOf(
            Triple(
                MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
                MigrationFailureDiagnosticStage.UP_EXECUTION,
                MigrationVersionState.STATUS_MIGRATE_STARTED,
            ),
            Triple(
                MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN,
                MigrationFailureDiagnosticStage.DOWN_EXECUTION,
                MigrationVersionState.STATUS_ROLLBACK_STARTED,
            ),
        )) {
            val cp = checkpoint(
                originalStatus,
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                "alpha",
                diagnostic(stage, "alpha"),
            )
            validator.validateExecutionFailure(status, VERSION_ID, cp, diagnostic(stage, "alpha"), registeredIds)

            assertFailsWith<IllegalArgumentException> {
                validator.validateExecutionFailure(
                    status,
                    VERSION_ID,
                    cp,
                    diagnostic(stage, "beta", versionId = VERSION_ID + 1),
                    registeredIds,
                )
            }
            assertFailsWith<IllegalArgumentException> {
                validator.validateExecutionFailure(status, VERSION_ID, cp, diagnostic(UP_STAGE, "alpha"), registeredIds)
            }
            assertFailsWith<IllegalArgumentException> {
                validator.validateExecutionFailure(
                    status,
                    VERSION_ID,
                    cp.copy(cursorMigrationId = "beta"),
                    diagnostic(stage, "alpha"),
                    registeredIds,
                )
            }
            assertFailsWith<IllegalArgumentException> {
                validator.validateExecutionFailure(
                    status,
                    VERSION_ID,
                    cp.copy(phase = MigrationRetryPhase.RECORD_PENDING),
                    diagnostic(stage, "alpha"),
                    registeredIds,
                )
            }
        }
    }

    @Test
    fun `accepts UP and DOWN recording diagnostics and cursor advancement null identity`() {
        validateRecord(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "alpha",
            currentId = "beta", // Valid Version member; latest Diagnostic need not equal cursor.
            originalId = "alpha",
        )
        validateRecord(
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "beta",
            currentId = null, // Source's cursor-advance failure is version-level.
            originalId = "alpha",
        )
    }

    @Test
    fun `accepts UP and DOWN finalization diagnostics only with null identity and cursor`() {
        validateRecord(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.FINALIZATION_PENDING,
            cursor = null,
            currentId = null,
            originalId = "alpha",
        )
        validateRecord(
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
            MigrationRetryPhase.FINALIZATION_PENDING,
            cursor = null,
            currentId = null,
            originalId = null,
        )

        assertRejected(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.FINALIZATION_PENDING,
            cursor = null,
            currentId = "alpha",
            originalId = null,
        )
    }

    @Test
    fun `rejects status direction version stage and unregistered migration mismatch`() {
        val cp = checkpoint(MigrationVersionState.STATUS_MIGRATE_STARTED, MigrationRetryPhase.RECORD_PENDING, "alpha", diagnostic(UP_STAGE, "alpha"))
        assertFailsWith<IllegalArgumentException> {
            validator.validateRecordingFailure(
                MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                VERSION_ID,
                cp,
                diagnostic(UP_STAGE, "alpha"),
                registeredIds,
            )
        }
        assertRejected(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, MigrationRetryPhase.RECORD_PENDING, "alpha", "alpha", null,
            current = diagnostic(UP_STAGE, "alpha", versionId = VERSION_ID + 1))
        assertRejected(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, MigrationRetryPhase.RECORD_PENDING, "alpha", "alpha", null,
            current = diagnostic(DOWN_STAGE, "alpha"))
        assertRejected(MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD, MigrationRetryPhase.RECORD_PENDING, "alpha", "foreign_id", null)
        assertRejected(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            "alpha",
            "alpha",
            null,
            current = diagnostic(UP_STAGE, "alpha").replace("\"migrationId\":\"alpha\"", "\"migrationId\":7"),
        )
    }

    @Test
    fun `rejects missing unknown duplicate malformed and unsupported diagnostic structure`() {
        val valid = diagnostic(UP_STAGE, "alpha")
        val invalids = listOf(
            valid.replace(",\"message\":\"diagnostic text\"", ""),
            valid.dropLast(1) + ",\"unknown\":1}",
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"schemaVersion\":1"),
            valid.replace("\"versionId\":$VERSION_ID", "\"versionId\":\"$VERSION_ID\""),
            valid.replace("\"migrationId\":\"alpha\"", "\"migrationId\":7"),
            valid.replace("\"exception\":\"java.sql.SQLException\"", "\"exception\":7"),
            valid.replace("\"sqlState\":\"42000\"", "\"sqlState\":42000"),
            valid.replace("\"errorCode\":73", "\"errorCode\":\"73\""),
            valid.replace("\"message\":\"diagnostic text\"", "\"message\":false"),
            valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            "{malformed",
        )
        invalids.forEach { malformed ->
            assertRejected(
                MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                MigrationRetryPhase.RECORD_PENDING,
                "alpha",
                "alpha",
                "alpha",
                current = malformed,
            )
        }
    }

    @Test
    fun `validates original diagnostic independently of the current cursor`() {
        validateRecord(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "beta",
            currentId = "beta",
            originalId = "alpha",
        )
        assertRejected(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "beta",
            currentId = "beta",
            originalId = "alpha",
            original = diagnostic(UP_STAGE, "alpha", versionId = VERSION_ID + 1),
        )
        assertRejected(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "beta",
            currentId = "beta",
            originalId = "alpha",
            original = diagnostic(DOWN_STAGE, "alpha"),
        )
        assertRejected(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "beta",
            currentId = "beta",
            originalId = "alpha",
            original = "",
        )
    }

    @Test
    fun `rejects an identity that does not follow MigrationIdentity grammar`() {
        val invalidIdentity = "Bad-ID"
        assertTrue(runCatching { MigrationIdentity.fromValue(invalidIdentity) }.isFailure)
        assertRejected(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationRetryPhase.RECORD_PENDING,
            cursor = "alpha",
            currentId = "alpha",
            originalId = null,
            current = diagnostic(UP_STAGE, "alpha")
                .replace("\"migrationId\":\"alpha\"", "\"migrationId\":\"$invalidIdentity\""),
        )
    }

    private fun validateRecord(
        status: Int,
        phase: MigrationRetryPhase,
        cursor: String?,
        currentId: String?,
        originalId: String?,
    ) {
        val up = status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
        val stage = if (up) UP_STAGE else DOWN_STAGE
        val cp = checkpoint(
            if (up) MigrationVersionState.STATUS_MIGRATE_STARTED else MigrationVersionState.STATUS_ROLLBACK_STARTED,
            phase,
            cursor,
            diagnostic(stage, originalId),
        )
        validator.validateRecordingFailure(status, VERSION_ID, cp, diagnostic(stage, currentId), registeredIds)
    }

    private fun assertRejected(
        status: Int,
        phase: MigrationRetryPhase,
        cursor: String?,
        currentId: String?,
        originalId: String?,
        current: String? = null,
        original: String? = null,
    ) {
        val up = status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
        val stage = if (up) UP_STAGE else DOWN_STAGE
        val cp = checkpoint(
            if (up) MigrationVersionState.STATUS_MIGRATE_STARTED else MigrationVersionState.STATUS_ROLLBACK_STARTED,
            phase,
            cursor,
            original ?: diagnostic(stage, originalId),
        )
        assertFailsWith<IllegalArgumentException> {
            validator.validateRecordingFailure(
                status,
                VERSION_ID,
                cp,
                current ?: currentId?.let { diagnostic(stage, it) },
                registeredIds,
            )
        }
    }

    private fun checkpoint(
        originalStatus: Int,
        phase: MigrationRetryPhase,
        cursor: String?,
        originalMessage: String?,
    ) = MigrationRetryCheckpoint(
        formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
        revision = 4,
        originalStatus = originalStatus,
        originalMessage = originalMessage,
        cursorMigrationId = cursor,
        phase = phase,
    )

    private fun diagnostic(
        stage: MigrationFailureDiagnosticStage,
        migrationId: String?,
        versionId: Int = VERSION_ID,
    ): String {
        return MigrationFailureDiagnostic(
            stage = stage,
            exceptionClassName = "java.sql.SQLException",
            sqlState = "42000",
            vendorErrorCode = 73,
            message = "diagnostic text",
            migrationId = migrationId,
        ).toStorageMessage(versionId)
    }

    private companion object {
        const val VERSION_ID = 41
        val UP_STAGE = MigrationFailureDiagnosticStage.UP_RECORDING
        val DOWN_STAGE = MigrationFailureDiagnosticStage.DOWN_RECORDING
    }
}
