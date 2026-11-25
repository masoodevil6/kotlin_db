package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.controller.MigrationController
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport
import kotlin.jvm.JvmOverloads

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

    /** Retries the validated UP activity for one Version, optionally continuing within that Version. */
    fun retryUp(
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
    ): ExecuteResult<Boolean> = controller.retryUp(target, toCompleteVersion, scope)

    /** Retries the validated DOWN activity for one Version, optionally continuing within that Version. */
    fun retryDown(
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
    ): ExecuteResult<Boolean> = controller.retryDown(target, toCompleteVersion, scope)

    /**
     * Recovers the latest incomplete Version when its persisted status and Checkpoint allow it.
     * Returns `Success(null)` when the latest-incomplete selection finds no Version.
     * Non-retryable, unknown, and manual-recovery states return Failure.
     */
    fun recovery(toCompleteVersion: Boolean = true): ExecuteResult<Boolean> =
        controller.recovery(scope, toCompleteVersion)

    fun status(): ExecuteResult<MigrationStatusReport> = controller.status(scope)

    fun current(): ExecuteResult<MigrationVersion?> = controller.current(scope)

    /** Reads the latest persisted Version state without running or changing any Migration. */
    @JvmOverloads
    @Deprecated("Use state(MigrationVersionSelection) or state(versionId) for a typed recovery target")
    fun state(lastIncompleteVersion: Boolean = false): ExecuteResult<MigrationVersionState?> =
        controller.state(scope, lastIncompleteVersion)

    /** Reads the latest Version selected by an explicit policy without executing any Migration. */
    fun state(selection: MigrationVersionSelection): ExecuteResult<MigrationRecoveryTarget?> =
        controller.state(scope, selection)

    /** Reads one persisted Version by ID without executing any Migration. */
    fun state(versionId: Int): ExecuteResult<MigrationRecoveryTarget?> =
        controller.state(scope, versionId)
}

/**
 * Runs a receiver block with the supplied configuration and exact Operation scope.
 * The block's return value is preserved; callers choose how to collect results when invoking
 * multiple commands, and each command returns its individual [ExecuteResult].
 */
internal fun <T> migrationFacade(
    configuration: MigrationConfiguration,
    scope: OperationExecutionScope,
    block: MigrationFacade.() -> T,
): T = MigrationFacade(configuration, scope).block()
