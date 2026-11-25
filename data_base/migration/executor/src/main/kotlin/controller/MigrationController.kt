package gog.my_project.data_base_migration.executor.controller

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.service.init.SystemMigrationInitializer
import gog.my_project.data_base_migration.executor.service.run.MigrationRunOrchestrationService
import gog.my_project.data_base_migration.executor.service.status.MigrationStatusService
import gog.my_project.data_base_migration.executor.service.version_selection.MigrationCurrentVersionService
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionStateService
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport
import kotlin.jvm.JvmOverloads


/** Public entry point for commands provided by the Migration executor. */
class MigrationController(
    configuration: MigrationConfiguration,
) {
    private val initializer = SystemMigrationInitializer()
    private val currentVersionService = MigrationCurrentVersionService(configuration)
    private val statusService = MigrationStatusService(configuration)
    private val stateService = MigrationVersionStateService(configuration)
    private val runOrchestrationService = MigrationRunOrchestrationService(configuration)

    /** Initializes the System Migration storage tables in the caller's Operation scope. */
    fun init(scope: OperationExecutionScope): ExecuteResult<Boolean> = initializer.init(scope)

    /**
     * Returns the currently applied public Migration Version, or `Success(null)` if none is applied.
     * The supplied Scope must be an active managed root Connection Scope with a canonical
     * [DatabaseIdentity], a MySQL or MariaDB dialect, and no active session advisory lock.
     * Unsupported Scopes fail through the existing Operation system-lock contract.
     */
    fun current(scope: OperationExecutionScope): ExecuteResult<MigrationVersion?> =
        when (val result = currentVersionService.currentVersion(scope)) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> ExecuteResult.Success(result.result?.version)
        }

    /** Returns the raw persisted state and metadata of the latest Version, or `Success(null)`. */
    @JvmOverloads
    @Deprecated("Use state(MigrationVersionSelection) or state(versionId) for a typed recovery target")
    fun state(
        scope: OperationExecutionScope,
        lastIncompleteVersion: Boolean = false,
    ): ExecuteResult<MigrationVersionState?> = stateService.state(scope, lastIncompleteVersion)

    /** Selects a Version by an explicit policy and resolves only an unambiguous Migration. */
    fun state(
        scope: OperationExecutionScope,
        selection: MigrationVersionSelection,
    ): ExecuteResult<MigrationRecoveryTarget?> = stateService.state(scope, selection)

    /** Selects one persisted Version by its ID and resolves only an unambiguous Migration. */
    fun state(
        scope: OperationExecutionScope,
        versionId: Int,
    ): ExecuteResult<MigrationRecoveryTarget?> = stateService.state(scope, versionId)

    /**
     * Reads and analyzes persisted Migration status under one Migration system lock.
     * The supplied Scope must be an active managed root Connection Scope with a canonical
     * [DatabaseIdentity], a MySQL or MariaDB dialect, and no active session advisory lock.
     * Unsupported Scopes fail through the existing Operation system-lock contract.
     */
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

    /** Retries one validated UP Migration Version without crossing into another Version. */
    fun retryUp(
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
        scope: OperationExecutionScope,
    ): ExecuteResult<Boolean> = runOrchestrationService.retryUp(target, toCompleteVersion, scope)

    /** Retries one validated DOWN Migration Version without crossing into another Version. */
    fun retryDown(
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
        scope: OperationExecutionScope,
    ): ExecuteResult<Boolean> = runOrchestrationService.retryDown(target, toCompleteVersion, scope)

    /** Selects and dispatches recovery for the latest incomplete Version. */
    fun recovery(
        scope: OperationExecutionScope,
        toCompleteVersion: Boolean = true,
    ): ExecuteResult<Boolean> = runOrchestrationService.recovery(scope, toCompleteVersion)

}
