package gog.my_project.data_base_migration.executor.service.version_selection

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionHistoryReader
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionHistoryValidator
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.prepared.PreparedMigrationVersion

/** Resolves the latest applied Version from the caller's persisted Migration history. */
internal class MigrationCurrentVersionService(
    private val configuration: MigrationConfiguration,
) {
    private val historyReader = MigrationVersionHistoryReader()
    private val historyValidator = MigrationVersionHistoryValidator()

    /** Returns the latest applied Version in Configuration order, or `Success(null)` when none exist. */
    fun currentVersion(scope: OperationExecutionScope): ExecuteResult<PreparedMigrationVersion?> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }

        return try {
            when (val readResult = historyReader.read(scope)) {
                is ExecuteResult.Failure -> readResult
                is ExecuteResult.Success -> {
                    val snapshot = requireNotNull(readResult.result) {
                        "Migration history reader returned no snapshot"
                    }
                    val history = historyValidator.validateConfiguration(configuration, snapshot)
                    ExecuteResult.Success(history.appliedVersions.lastOrNull())
                }
            }
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed(failure)
        }
    }
}
