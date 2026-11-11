package gog.my_project.data_base_migration.executor.controller

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.service.init.SystemMigrationInitializer
import gog.my_project.data_base_migration.executor.service.run.MigrationRunOrchestrationService
import gog.my_project.data_base_migration.executor.service.status.MigrationStatusService
import gog.my_project.data_base_migration.executor.service.version_selection.MigrationCurrentVersionService
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport


/** Public entry point for commands provided by the Migration executor. */
class MigrationController(
    configuration: MigrationConfiguration,
) {
    private val initializer = SystemMigrationInitializer()
    private val currentVersionService = MigrationCurrentVersionService(configuration)
    private val statusService = MigrationStatusService(configuration)
    private val runOrchestrationService = MigrationRunOrchestrationService(configuration)

    /** Initializes the System Migration storage tables in the caller's Operation scope. */
    fun init(scope: OperationExecutionScope): ExecuteResult<Boolean> = initializer.init(scope)

    /** Returns the currently applied public Migration Version, or `Success(null)` if none is applied. */
    fun current(scope: OperationExecutionScope): ExecuteResult<MigrationVersion?> =
        when (val result = currentVersionService.currentVersion(scope)) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> ExecuteResult.Success(result.result?.version)
        }

    /** Reads and analyzes persisted migration status in the caller's Operation scope. */
    fun status(scope: OperationExecutionScope): ExecuteResult<MigrationStatusReport> =
        statusService.status(scope)

    /** Applies every pending Version in Configuration order. */
    fun migrate(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        runOrchestrationService.migrate(scope)

    /** Applies pending Versions through and including [version]. */
    fun migrate(version: MigrationVersionTag, scope: OperationExecutionScope): ExecuteResult<Boolean> =
        runOrchestrationService.migrate(version, scope)

    /** Rolls back the latest applied Version. */
    fun rollback(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        runOrchestrationService.rollback(scope)

    /** Rolls back applied Versions strictly newer than [version], preserving the target. */
    fun rollback(version: MigrationVersionTag, scope: OperationExecutionScope): ExecuteResult<Boolean> =
        runOrchestrationService.rollback(version, scope)

}
