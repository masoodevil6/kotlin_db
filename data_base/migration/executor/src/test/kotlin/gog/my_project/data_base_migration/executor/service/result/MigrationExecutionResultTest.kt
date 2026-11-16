package gog.my_project.data_base_migration.executor.service.result

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationExecutionResultTest {
    @Test
    fun `success supports both directions`() {
        val versionTag = versionTag("v1")
        val migrationId = MigrationIdentity.fromValue("create_users")

        val up = MigrationExecutionResult.Success(
            direction = MigrationExecutionResult.Direction.UP,
            versionTag = versionTag,
            versionRecordId = 12,
            migrationId = migrationId,
        )
        val down = MigrationExecutionResult.Success(
            direction = MigrationExecutionResult.Direction.DOWN,
            versionTag = versionTag,
            versionRecordId = 12,
            migrationId = migrationId,
        )

        assertEquals(MigrationExecutionResult.Direction.UP, up.direction)
        assertEquals(MigrationExecutionResult.Direction.DOWN, down.direction)
        assertSame(versionTag, up.versionTag)
        assertEquals(12, up.versionRecordId)
        assertEquals(migrationId, up.migrationId)
        assertEquals(migrationId, down.migrationId)
    }

    @Test
    fun `failure supports every stage and preserves the original throwable`() {
        val versionTag = versionTag("v1")
        val migrationId = MigrationIdentity.fromValue("create_users")

        MigrationExecutionResult.FailureStage.values().forEach { stage ->
            val cause = IllegalStateException("failure at $stage")
            val result = MigrationExecutionResult.Failure(
                direction = MigrationExecutionResult.Direction.UP,
                versionTag = versionTag,
                versionRecordId = 12,
                migrationId = migrationId,
                stage = stage,
                cause = cause,
            )

            assertEquals(stage, result.stage)
            assertSame(cause, result.cause)
        }
    }

    @Test
    fun `fatal supports both directions and preserves the original error`() {
        val versionTag = versionTag("v1")

        MigrationExecutionResult.Direction.values().forEach { direction ->
            val error = AssertionError("fatal $direction")
            val result = MigrationExecutionResult.Fatal(
                direction = direction,
                versionTag = versionTag,
                versionRecordId = null,
                migrationId = null,
                stage = MigrationExecutionResult.FailureStage.EXECUTION,
                error = error,
            )

            assertEquals(direction, result.direction)
            assertSame(error, result.error)
        }
    }

    @Test
    fun `identifiers may be absent before their records exist`() {
        val result = MigrationExecutionResult.Failure(
            direction = MigrationExecutionResult.Direction.UP,
            versionTag = null,
            versionRecordId = null,
            migrationId = null,
            stage = MigrationExecutionResult.FailureStage.VERSION_RECORD,
            cause = IllegalStateException("Version insert failed"),
        )

        assertNull(result.versionTag)
        assertNull(result.versionRecordId)
        assertNull(result.migrationId)
    }

    @Test
    fun `error cannot be represented as an ordinary failure`() {
        val error = AssertionError("must remain fatal")

        val thrown = assertFailsWith<IllegalArgumentException> {
            MigrationExecutionResult.Failure(
                direction = MigrationExecutionResult.Direction.DOWN,
                versionTag = null,
                versionRecordId = null,
                migrationId = null,
                stage = MigrationExecutionResult.FailureStage.EXECUTION,
                cause = error,
            )
        }

        assertTrue(thrown.message.orEmpty().contains("Fatal Errors must be represented"))
    }

    private fun versionTag(value: String): MigrationVersionTag = object : MigrationVersionTag {
        override val value: String = value
    }
}
