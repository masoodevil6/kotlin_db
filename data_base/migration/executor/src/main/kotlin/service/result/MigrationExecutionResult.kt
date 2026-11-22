package gog.my_project.data_base_migration.executor.service.result

import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag

/** Internal outcome for one Migration execution attempt. */
internal sealed interface MigrationExecutionResult {
    val direction: Direction
    val versionTag: MigrationVersionTag?
    val versionRecordId: Int?
    val migrationId: MigrationIdentity?

    enum class Direction {
        UP,
        DOWN,
    }

    enum class FailureStage {
        EXECUTION,
        HISTORY_RECORD,
        RECONCILIATION,
        VERSION_RECORD,
    }

    data class Success(
        override val direction: Direction,
        override val versionTag: MigrationVersionTag?,
        override val versionRecordId: Int?,
        override val migrationId: MigrationIdentity,
    ) : MigrationExecutionResult

    data class Failure(
        override val direction: Direction,
        override val versionTag: MigrationVersionTag?,
        override val versionRecordId: Int?,
        override val migrationId: MigrationIdentity?,
        val stage: FailureStage,
        val cause: Throwable,
    ) : MigrationExecutionResult {
        init {
            require(cause !is Error) { "Fatal Errors must be represented by MigrationExecutionResult.Fatal" }
        }
    }

    data class Fatal(
        override val direction: Direction,
        override val versionTag: MigrationVersionTag?,
        override val versionRecordId: Int?,
        override val migrationId: MigrationIdentity?,
        val stage: FailureStage,
        val error: Error,
    ) : MigrationExecutionResult
}
