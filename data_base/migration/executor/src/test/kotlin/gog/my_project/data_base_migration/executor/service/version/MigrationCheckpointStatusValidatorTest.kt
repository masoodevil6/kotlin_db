package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MigrationCheckpointStatusValidatorTest {
    private val validator = MigrationCheckpointStatusValidator()

    @Test
    fun `started UP and DOWN statuses classify all five checkpoint phases`() {
        val expectedByPhase = mapOf(
            MigrationRetryPhase.EXECUTION_PENDING to MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE,
            MigrationRetryPhase.EXECUTION_IN_FLIGHT to MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY,
            MigrationRetryPhase.RECORD_PENDING to MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE,
            MigrationRetryPhase.PAUSED_NEXT to MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE,
            MigrationRetryPhase.FINALIZATION_PENDING to MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE,
        )

        listOf(
            MigrationVersionState.STATUS_MIGRATE_STARTED to MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE,
            MigrationVersionState.STATUS_ROLLBACK_STARTED to MigrationCheckpointStatusKind.DOWN_EXECUTION_RETRYABLE,
        ).forEach { (status, expectedExecution) ->
            val up = status == MigrationVersionState.STATUS_MIGRATE_STARTED
            MigrationRetryPhase.entries.forEach { phase ->
                val expected = when (expectedByPhase.getValue(phase)) {
                    MigrationCheckpointStatusKind.UP_EXECUTION_RETRYABLE -> expectedExecution
                    MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE ->
                        if (up) MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE
                        else MigrationCheckpointStatusKind.DOWN_RECORDING_RETRYABLE
                    MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY ->
                        if (up) MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY
                        else MigrationCheckpointStatusKind.DOWN_MANUAL_RECOVERY
                    else -> error("Unexpected phase mapping")
                }
                val result = validator.assess(status, encode(up, phase))
                assertEquals(expected, result.kind, "status=$status phase=$phase")
                if (phase == MigrationRetryPhase.EXECUTION_IN_FLIGHT) {
                    assertNotNull(result.reason, "status=$status phase=$phase")
                } else {
                    assertNull(result.reason, "status=$status phase=$phase")
                }
            }
        }
    }

    @Test
    fun `recording failure statuses allow only recording and finalization phases with original diagnostic`() {
        val cases = listOf(
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD to true,
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD to false,
        )
        cases.forEach { (status, up) ->
            listOf(MigrationRetryPhase.RECORD_PENDING, MigrationRetryPhase.FINALIZATION_PENDING).forEach { phase ->
                val expected = if (up) {
                    MigrationCheckpointStatusKind.UP_RECORDING_RETRYABLE
                } else {
                    MigrationCheckpointStatusKind.DOWN_RECORDING_RETRYABLE
                }
                assertEquals(expected, validator.assess(status, encode(up, phase)).kind)
            }
            listOf(
                MigrationRetryPhase.EXECUTION_PENDING,
                MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                MigrationRetryPhase.PAUSED_NEXT,
            ).forEach { phase ->
                assertEquals(MigrationCheckpointStatusKind.INVALID, validator.assess(status, encode(up, phase)).kind)
            }
            assertEquals(
                MigrationCheckpointStatusKind.INVALID,
                validator.assess(status, encode(up, MigrationRetryPhase.RECORD_PENDING, originalMessage = null)).kind,
            )
            assertEquals(
                MigrationCheckpointStatusKind.INVALID,
                validator.assess(status, encode(!up, MigrationRetryPhase.RECORD_PENDING)).kind,
            )
        }
    }

    @Test
    fun `legacy execution failure statuses remain non retryable for every valid phase`() {
        val cases = listOf(
            MigrationVersionState.STATUS_MIGRATE_FAILED_UP to true,
            MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN to false,
        )
        cases.forEach { (status, up) ->
            val expected = if (up) {
                MigrationCheckpointStatusKind.UP_EXECUTION_FAILURE_NOT_RETRYABLE
            } else {
                MigrationCheckpointStatusKind.DOWN_EXECUTION_FAILURE_NOT_RETRYABLE
            }
            MigrationRetryPhase.entries.forEach { phase ->
                assertEquals(expected, validator.assess(status, encode(up, phase)).kind)
            }
            assertEquals(expected, validator.assess(status, null).kind)
            assertEquals(
                MigrationCheckpointStatusKind.INVALID,
                validator.assess(status, encode(!up, MigrationRetryPhase.EXECUTION_PENDING)).kind,
            )
        }
    }

    @Test
    fun `manual statuses remain blocked even when checkpoint is absent malformed or direction mismatched`() {
        val cases = listOf(
            MigrationVersionState.STATUS_MIGRATE_MANUAL_RECOVERY_REQUIRED to
                MigrationCheckpointStatusKind.UP_MANUAL_RECOVERY,
            MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED to
                MigrationCheckpointStatusKind.DOWN_MANUAL_RECOVERY,
        )
        cases.forEach { (status, kind) ->
            assertEquals(kind, validator.assess(status, null).kind)
            assertEquals(kind, validator.assess(status, "not-json").kind)
            val oppositeDirection = status == MigrationVersionState.STATUS_ROLLBACK_MANUAL_RECOVERY_REQUIRED
            val mismatch = validator.assess(status, encode(oppositeDirection, MigrationRetryPhase.EXECUTION_PENDING))
            assertEquals(kind, mismatch.kind)
            assertNotNull(mismatch.reason)
            assertNull(mismatch.checkpoint)
        }
    }

    @Test
    fun `completed unknown and malformed status checkpoint combinations fail closed`() {
        assertEquals(
            MigrationCheckpointStatusKind.COMPLETED,
            validator.assess(MigrationVersionState.STATUS_MIGRATE_COMPLETED, null).kind,
        )
        assertEquals(
            MigrationCheckpointStatusKind.INVALID,
            validator.assess(MigrationVersionState.STATUS_MIGRATE_COMPLETED, encode(true, MigrationRetryPhase.FINALIZATION_PENDING)).kind,
        )
        assertEquals(MigrationCheckpointStatusKind.INVALID, validator.assess(999, null).kind)
        assertEquals(MigrationCheckpointStatusKind.INVALID, validator.assess(999, "not-json").kind)

        listOf(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
            MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
        ).forEach { status ->
            assertEquals(MigrationCheckpointStatusKind.INVALID, validator.assess(status, null).kind)
            assertEquals(MigrationCheckpointStatusKind.INVALID, validator.assess(status, "not-json").kind)
        }

        val invalidCursor = encode(true, MigrationRetryPhase.EXECUTION_PENDING)
            .replace("create_tables", "Invalid-ID")
        assertEquals(
            MigrationCheckpointStatusKind.INVALID,
            validator.assess(MigrationVersionState.STATUS_MIGRATE_STARTED, invalidCursor).kind,
        )
        assertEquals(
            MigrationCheckpointStatusKind.INVALID,
            validator.assess(
                MigrationVersionState.STATUS_ROLLBACK_STARTED,
                encode(true, MigrationRetryPhase.EXECUTION_PENDING),
            ).kind,
        )
    }

    private fun encode(
        up: Boolean,
        phase: MigrationRetryPhase,
        originalMessage: String? = "original diagnostic",
    ): String = MigrationRetryCheckpointCodec.encode(
        MigrationRetryCheckpoint(
            formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
            revision = 0,
            originalStatus = if (up) {
                MigrationVersionState.STATUS_MIGRATE_STARTED
            } else {
                MigrationVersionState.STATUS_ROLLBACK_STARTED
            },
            originalMessage = originalMessage,
            cursorMigrationId = if (phase == MigrationRetryPhase.FINALIZATION_PENDING) null else "create_tables",
            phase = phase,
        ),
    )
}
