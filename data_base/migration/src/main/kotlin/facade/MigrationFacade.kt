package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.controller.MigrationController
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport

/**
 * Consumer-facing commands for one Migration Configuration and one caller-owned Operation scope.
 * Each command executes immediately and returns the Controller's result unchanged.
 */
class MigrationFacade internal constructor(
    configuration: MigrationConfiguration,
    private val scope: OperationExecutionScope,
) {
    private val controller = MigrationController(configuration)

    fun init(): ExecuteResult<Boolean> = controller.init(scope)

    fun migrate(): ExecuteResult<Boolean> = controller.migrate(scope)

    fun migrate(version: MigrationVersionTag): ExecuteResult<Boolean> =
        controller.migrate(version, scope)

    fun rollback(): ExecuteResult<Boolean> = controller.rollback(scope)

    fun rollback(version: MigrationVersionTag): ExecuteResult<Boolean> =
        controller.rollback(version, scope)

    fun status(): ExecuteResult<MigrationStatusReport> = controller.status(scope)

    fun current(): ExecuteResult<MigrationVersion?> = controller.current(scope)
}

/**
 * Runs a receiver block with the supplied configuration and exact Operation scope.
 * The block's return value is preserved; callers choose how to collect results when invoking
 * multiple commands, and each command returns its individual [ExecuteResult].
 */
fun <T> migrationFacade(
    configuration: MigrationConfiguration,
    scope: OperationExecutionScope,
    block: MigrationFacade.() -> T,
): T = MigrationFacade(configuration, scope).block()
