package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport

/** Coordinates read-only status collection and analysis in the caller-owned Operation scope. */
internal class MigrationStatusService(
    private val configuration: MigrationConfiguration,
    private val reader: MigrationStatusReader = MigrationStatusReader(),
    private val analyzer: MigrationStatusAnalyzer = MigrationStatusAnalyzer(),
) {
    fun status(scope: OperationExecutionScope): ExecuteResult<MigrationStatusReport> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }

        return try {
            when (val readResult = reader.read(scope)) {
                is ExecuteResult.Failure -> readResult
                is ExecuteResult.Success -> {
                    val snapshot = requireNotNull(readResult.result) {
                        "Migration status reader returned no snapshot"
                    }
                    ExecuteResult.Success(analyzer.analyze(configuration, snapshot))
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
