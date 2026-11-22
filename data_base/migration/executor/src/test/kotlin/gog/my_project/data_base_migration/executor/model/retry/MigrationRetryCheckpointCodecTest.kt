package gog.my_project.data_base_migration.executor.model.retry

import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MigrationRetryCheckpointCodecTest {
    private val initial = MigrationRetryCheckpoint(
        formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
        revision = 0,
        originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
        originalMessage = null,
        cursorMigrationId = "create_tables",
        phase = MigrationRetryPhase.EXECUTION_PENDING,
    )

    @Test
    fun `round trips deterministically and preserves original diagnostic exactly`() {
        val diagnostic = "{\"schemaVersion\":1,\"message\":\"é\uD83D\uDE80 \\\"quoted\\\"\"}"
        val checkpoint = initial.copy(
            revision = 12,
            originalMessage = diagnostic,
            phase = MigrationRetryPhase.RECORD_PENDING,
        )

        val encoded = MigrationRetryCheckpointCodec.encode(checkpoint)

        assertEquals(encoded, MigrationRetryCheckpointCodec.encode(checkpoint))
        assertEquals(checkpoint, MigrationRetryCheckpointCodec.decode(encoded))
        assertEquals(diagnostic, MigrationRetryCheckpointCodec.decode(encoded).originalMessage)
    }

    @Test
    fun `rejects duplicate unknown and missing fields`() {
        val valid = MigrationRetryCheckpointCodec.encode(initial)

        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(valid.replace("{\"formatVersion\":1,", "{\"formatVersion\":1,\"formatVersion\":1,"))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(valid.replace("{\"formatVersion\":1,", "{\"unexpected\":0,\"formatVersion\":1,"))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(valid.replace(Regex("\\,\\\"phase\\\":\\\"EXECUTION_PENDING\\\""), ""))
        }
    }

    @Test
    fun `rejects malformed json invalid values and unsupported format`() {
        assertFailsWith<IllegalArgumentException> { MigrationRetryCheckpointCodec.decode("{not-json}") }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(MigrationRetryCheckpointCodec.encode(initial).replace("create_tables", "Bad-ID"))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(MigrationRetryCheckpointCodec.encode(initial).replace("EXECUTION_PENDING", "UNKNOWN"))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(MigrationRetryCheckpointCodec.encode(initial).replace("\"formatVersion\":1", "\"formatVersion\":2"))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(MigrationRetryCheckpointCodec.encode(initial).replace("\"revision\":0", "\"revision\":-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.decode(MigrationRetryCheckpointCodec.encode(initial).replace("\"revision\":0", "\"revision\":9223372036854775808"))
        }
    }

    @Test
    fun `enforces direction and phase cursor consistency`() {
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.encode(initial.copy(originalStatus = 101))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.encode(initial.copy(phase = MigrationRetryPhase.FINALIZATION_PENDING))
        }
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.encode(
                initial.copy(cursorMigrationId = null, phase = MigrationRetryPhase.RECORD_PENDING),
            )
        }
        val finalization = initial.copy(cursorMigrationId = null, phase = MigrationRetryPhase.FINALIZATION_PENDING)
        assertNull(MigrationRetryCheckpointCodec.decode(MigrationRetryCheckpointCodec.encode(finalization)).cursorMigrationId)
    }

    @Test
    fun `accepts exact utf8 byte limit and rejects the next byte without truncation`() {
        val emptyMessageBytes = MigrationRetryCheckpointCodec.encode(initial.copy(originalMessage = ""))
            .toByteArray(Charsets.UTF_8).size
        val exactLength = MigrationRetryCheckpointCodec.MAX_UTF8_BYTES - emptyMessageBytes
        val exact = initial.copy(originalMessage = "x".repeat(exactLength))
        assertEquals(MigrationRetryCheckpointCodec.MAX_UTF8_BYTES, MigrationRetryCheckpointCodec.encode(exact).toByteArray(Charsets.UTF_8).size)
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.encode(exact.copy(originalMessage = "x".repeat(exactLength + 1)))
        }
    }

    @Test
    fun `counts multibyte utf8 and rejects oversize without truncating original message`() {
        val message = "🚀".repeat(4_100)
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.encode(initial.copy(originalMessage = message))
        }
        assertEquals(message, initial.copy(originalMessage = message).originalMessage)
    }

    @Test
    fun `rejects revision overflow`() {
        assertFailsWith<IllegalArgumentException> {
            MigrationRetryCheckpointCodec.nextRevision(initial.copy(revision = Long.MAX_VALUE))
        }
        assertEquals(1L, MigrationRetryCheckpointCodec.nextRevision(initial))
    }
}
