package gog.my_project.data_base_migration.executor.service.init

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationTable
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationTagTable
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationVersionTable
import gog.my_project.data_base_migration.executor.service.withMigrationLock
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_schema.Schema
import java.sql.Types

/** Creates the system migration storage tables without running user migrations. */
internal class SystemMigrationInitializer {
    /** Creates tag, version, then migration storage in the caller's Operation scope. */
    fun init(scope: OperationExecutionScope): ExecuteResult<Boolean> = safely(scope) {
        val tagResult = Schema.execute(
            SystemMigrationTagTable.definition(),
            scope,
        )
        if (tagResult is ExecuteResult.Failure) return@safely tagResult

        val versionResult = Schema.execute(
            SystemMigrationVersionTable.definition(),
            scope,
        )
        if (versionResult is ExecuteResult.Failure) return@safely versionResult

        when (val upgrade = ensureRetryCheckpointColumn(scope)) {
            is ExecuteResult.Failure -> return@safely upgrade
            is ExecuteResult.Success -> if (upgrade.result != true) {
                return@safely scope.failed(IllegalStateException("Retry Checkpoint column upgrade did not complete"))
            }
        }

        val migrationResult = Schema.execute(
            SystemMigrationTable.definition(),
            scope,
        )
        if (migrationResult is ExecuteResult.Failure) return@safely migrationResult

        ExecuteResult.Success(true)
    }

    /** Adds only this persisted protocol column, taking the Migration lock only when absent. */
    private fun ensureRetryCheckpointColumn(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        when (val initial = retryCheckpointColumnExists(scope)) {
            is ExecuteResult.Failure -> initial
            is ExecuteResult.Success -> if (initial.result == true) {
                ExecuteResult.Success(true)
            } else {
                withMigrationLock(scope) { lockedScope ->
                    when (val rechecked = retryCheckpointColumnExists(lockedScope)) {
                        is ExecuteResult.Failure -> rechecked
                        is ExecuteResult.Success -> if (rechecked.result == true) {
                            ExecuteResult.Success(true)
                        } else {
                            when (
                                val altered = lockedScope.executeDdl(
                                    "ALTER TABLE `${SystemMigrationVersionTable.NAME}` " +
                                        "ADD COLUMN `$RETRY_CHECKPOINT_COLUMN` TEXT NULL",
                                )
                            ) {
                                is ExecuteResult.Failure -> altered
                                is ExecuteResult.Success -> if (altered.result == true) {
                                    ExecuteResult.Success(true)
                                } else {
                                    lockedScope.failed(
                                        IllegalStateException("Retry Checkpoint column ALTER did not complete"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun retryCheckpointColumnExists(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        scope.executeRead(
            "SELECT DATABASE() AS selected_schema, COUNT(*) AS column_count " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = :table_name AND COLUMN_NAME = :column_name",
            listOf(
                OperationParameter("table_name", SystemMigrationVersionTable.NAME, Types.VARCHAR),
                OperationParameter("column_name", RETRY_CHECKPOINT_COLUMN, Types.VARCHAR),
            ),
        ) { rows ->
            check(rows.next()) { "Retry Checkpoint column probe returned no row" }
            val schemaName = requireNotNull(rows.getString("selected_schema")) {
                "A selected database is required to upgrade System Migration tables"
            }
            require(schemaName.isNotBlank()) { "Selected database name is blank" }
            val count = requireNotNull(rows.getLong("column_count")) {
                "Retry Checkpoint column probe returned no count"
            }
            check(!rows.next()) { "Retry Checkpoint column probe returned multiple rows" }
            count > 0
        }

    private inline fun <T> safely(
        scope: OperationExecutionScope,
        block: () -> ExecuteResult<T>,
    ): ExecuteResult<T> = try {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        block().also(scope::recordResult)
    } catch (error: Error) {
        scope.recordFatal(error)
        throw error
    } catch (failure: Throwable) {
        scope.failed(failure)
    }

    private companion object {
        const val RETRY_CHECKPOINT_COLUMN = "retry_checkpoint"
    }
}
