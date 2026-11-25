package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.sql.Types

/** Result of one checkpoint compare-and-set; ambiguous outcomes are terminal for this invocation. */
internal enum class MigrationCheckpointWriteResult {
    APPLIED,
    NOT_APPLIED,
    INDETERMINATE,
}

/** Fixed-purpose, parameterized persistence operations for the Migration retry protocol. */
internal class MigrationVersionCheckpointPersistence {
    fun insertUpVersion(
        scope: OperationExecutionScope,
        tagId: Int,
        checkpoint: String,
    ): ExecuteResult<Int> {
        val inserted = scope.executeInsert(
            BoundQuery(
                "INSERT INTO system_migration_version (tag_id, status, message, retry_checkpoint) VALUES (?, ?, ?, ?)",
                listOf(
                    intBinding("tagId", tagId),
                    intBinding("status", MigrationVersionState.STATUS_MIGRATE_STARTED),
                    stringBinding("message", null),
                    stringBinding("retryCheckpoint", checkpoint),
                ),
            ),
        )
        return when (inserted) {
            is ExecuteResult.Failure -> inserted
            is ExecuteResult.Success -> {
                val id = inserted.result
                    ?: return scope.failed(IllegalStateException("Migration Version INSERT returned no generated id"))
                if (id !in 1..Int.MAX_VALUE.toLong()) {
                    return scope.failed(IllegalStateException("Migration Version id $id is outside the supported Int range"))
                }
                ExecuteResult.Success(id.toInt())
            }
        }
    }

    /** Starts DOWN only from a completed legacy/current Version with no existing checkpoint. */
    fun startDown(
        scope: OperationExecutionScope,
        versionId: Int,
        checkpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> = update(
        scope,
        "UPDATE system_migration_version SET status = ?, retry_checkpoint = ? " +
            "WHERE id = ? AND status = ? AND retry_checkpoint IS NULL",
        listOf(
            intBinding("targetStatus", MigrationVersionState.STATUS_ROLLBACK_STARTED),
            stringBinding("retryCheckpoint", checkpoint),
            intBinding("versionId", versionId),
            intBinding("expectedStatus", MigrationVersionState.STATUS_MIGRATE_COMPLETED),
        ),
    )

    /** CASes only the Checkpoint while leaving the persisted lifecycle Status unchanged. */
    fun advanceCheckpoint(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedStatus: Int,
        expectedCheckpoint: String,
        newCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> {
        val checkpointPredicate = checkpointPredicate(expectedCheckpoint)
        return update(
            scope,
            "UPDATE system_migration_version SET retry_checkpoint = ? " +
                "WHERE id = ? AND status = ? AND ${checkpointPredicate.sql}",
            listOf(
                stringBinding("newCheckpoint", newCheckpoint),
                intBinding("versionId", versionId),
                intBinding("expectedStatus", expectedStatus),
            ) + checkpointPredicate.bindings,
        )
    }

    /** Normalizes a recording-only UP failure without changing its checkpoint or revision. */
    fun normalizeUpRecordingFailure(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> = normalizeRecordingFailure(
        scope,
        versionId,
        MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
        MigrationVersionState.STATUS_MIGRATE_STARTED,
        expectedCheckpoint,
    )

    /** Normalizes a recording-only DOWN failure without changing its checkpoint or revision. */
    fun normalizeDownRecordingFailure(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> = normalizeRecordingFailure(
        scope,
        versionId,
        MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
        MigrationVersionState.STATUS_ROLLBACK_STARTED,
        expectedCheckpoint,
    )

    /** Allows an explicitly requested UP Retry to leave status 101 while preserving its in-flight cursor. */
    fun normalizeUpExecutionFailure(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> = normalizeExecutionFailure(
        scope,
        versionId,
        MigrationVersionState.STATUS_MIGRATE_FAILED_UP,
        MigrationVersionState.STATUS_MIGRATE_STARTED,
        expectedCheckpoint,
    )

    /** Allows an explicitly requested DOWN Retry to leave status 201 while preserving its in-flight cursor. */
    fun normalizeDownExecutionFailure(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> = normalizeExecutionFailure(
        scope,
        versionId,
        MigrationVersionState.STATUS_ROLLBACK_FAILED_DOWN,
        MigrationVersionState.STATUS_ROLLBACK_STARTED,
        expectedCheckpoint,
    )

    /** Completes UP and removes its checkpoint atomically. */
    fun finalizeUp(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> {
        val checkpointPredicate = checkpointPredicate(expectedCheckpoint)
        return update(
            scope,
            "UPDATE system_migration_version SET status = ?, retry_checkpoint = NULL " +
                "WHERE id = ? AND status = ? AND ${checkpointPredicate.sql}",
            listOf(
                intBinding("targetStatus", MigrationVersionState.STATUS_MIGRATE_COMPLETED),
                intBinding("versionId", versionId),
                intBinding("expectedStatus", MigrationVersionState.STATUS_MIGRATE_STARTED),
            ) + checkpointPredicate.bindings,
        )
    }

    /** Deletes DOWN's Version only when its finalization cursor still matches exactly. */
    fun finalizeDown(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> {
        val checkpointPredicate = checkpointPredicate(expectedCheckpoint)
        val result = scope.executeDelete(
            BoundQuery(
                "DELETE FROM system_migration_version WHERE id = ? AND status = ? AND ${checkpointPredicate.sql}",
                listOf(
                    intBinding("versionId", versionId),
                    intBinding("expectedStatus", MigrationVersionState.STATUS_ROLLBACK_STARTED),
                ) + checkpointPredicate.bindings,
            ),
        )
        return classify(result)
    }

    private fun normalizeRecordingFailure(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedStatus: Int,
        targetStatus: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> {
        val checkpointPredicate = checkpointPredicate(expectedCheckpoint)
        return update(
            scope,
            "UPDATE system_migration_version SET status = ? " +
                "WHERE id = ? AND status = ? AND ${checkpointPredicate.sql}",
            listOf(
                intBinding("targetStatus", targetStatus),
                intBinding("versionId", versionId),
                intBinding("expectedStatus", expectedStatus),
            ) + checkpointPredicate.bindings,
        )
    }

    private fun normalizeExecutionFailure(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedStatus: Int,
        targetStatus: Int,
        expectedCheckpoint: String,
    ): ExecuteResult<MigrationCheckpointWriteResult> {
        val checkpointPredicate = checkpointPredicate(expectedCheckpoint)
        return update(
            scope,
            "UPDATE system_migration_version SET status = ? " +
                "WHERE id = ? AND status = ? AND ${checkpointPredicate.sql}",
            listOf(
                intBinding("targetStatus", targetStatus),
                intBinding("versionId", versionId),
                intBinding("expectedStatus", expectedStatus),
            ) + checkpointPredicate.bindings,
        )
    }

    private fun update(
        scope: OperationExecutionScope,
        sql: String,
        bindings: List<QueryParameterBinding>,
    ): ExecuteResult<MigrationCheckpointWriteResult> = classify(
        scope.executeUpdate(BoundQuery(sql, bindings)),
    )

    private fun classify(result: ExecuteResult<Int>): ExecuteResult<MigrationCheckpointWriteResult> = when (result) {
        is ExecuteResult.Failure -> result
        is ExecuteResult.Success -> ExecuteResult.Success(
            when (result.result) {
                0 -> MigrationCheckpointWriteResult.NOT_APPLIED
                1 -> MigrationCheckpointWriteResult.APPLIED
                else -> MigrationCheckpointWriteResult.INDETERMINATE
            },
        )
    }

    private fun checkpointPredicate(expected: String?): CheckpointPredicate = if (expected == null) {
        CheckpointPredicate("retry_checkpoint IS NULL", emptyList())
    } else {
        CheckpointPredicate(
            "CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
            listOf(stringBinding("expectedCheckpoint", expected)),
        )
    }

    private data class CheckpointPredicate(
        val sql: String,
        val bindings: List<QueryParameterBinding>,
    )

    private fun intBinding(name: String, value: Int) = QueryParameterBinding(name, value, Types.INTEGER)
    private fun stringBinding(name: String, value: String?) = QueryParameterBinding(name, value, Types.VARCHAR)
}
