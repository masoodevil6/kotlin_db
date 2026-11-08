package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.version_history.MigrationVersionHistorySnapshot
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService
import java.util.ArrayList
import java.util.Collections

internal sealed interface MigrationVersionTraversalRequest {
    data object MigrateAll : MigrationVersionTraversalRequest
    data class MigrateTo(val target: MigrationVersionTag) : MigrationVersionTraversalRequest
    data object RollbackLatest : MigrationVersionTraversalRequest
    data class RollbackTo(val target: MigrationVersionTag) : MigrationVersionTraversalRequest
}

internal enum class MigrationVersionTraversalDirection { UP, DOWN }

/** An immutable ordered instruction list containing no Scope, Connection, or callback. */
internal class MigrationVersionTraversalPlan(
    val direction: MigrationVersionTraversalDirection,
    versions: List<PreparedMigrationVersion>,
) {
    val versions: List<PreparedMigrationVersion> = Collections.unmodifiableList(ArrayList(versions))
}

/**
 * Builds deterministic plans from a consistent history snapshot without executing migrations or
 * opening a Connection or Transaction. The future coordinator must acquire its Migration Lock
 * before calling [plan] and retain it until every Version in the returned plan has completed.
 */
internal class MigrationVersionTraversalService(
    private val configuration: MigrationConfiguration,
    private val historyReader: MigrationVersionHistoryReader = MigrationVersionHistoryReader(),
    private val historyValidator: MigrationVersionHistoryValidator = MigrationVersionHistoryValidator(),
    private val preparationService: MigrationVersionPreparationService = MigrationVersionPreparationService(),
) {
    /** Reads using the supplied parent scope; this method never opens a child scope or transaction. */
    fun plan(
        scope: OperationExecutionScope,
        request: MigrationVersionTraversalRequest,
    ): ExecuteResult<MigrationVersionTraversalPlan> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        return try {
            when (val snapshotResult = historyReader.read(scope)) {
                is ExecuteResult.Failure -> snapshotResult
                is ExecuteResult.Success -> ExecuteResult.Success(plan(requireNotNull(snapshotResult.result), request))
            }
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed(failure)
        }
    }

    /** Pure planning entry point after the caller has supplied a complete history snapshot. */
    internal fun plan(
        snapshot: MigrationVersionHistorySnapshot,
        request: MigrationVersionTraversalRequest,
    ): MigrationVersionTraversalPlan {
        val history = historyValidator.validateConfiguration(configuration, snapshot)
        val appliedCount = history.appliedVersions.size

        return when (request) {
            MigrationVersionTraversalRequest.MigrateAll -> MigrationVersionTraversalPlan(
                MigrationVersionTraversalDirection.UP,
                configuration.registrations.drop(appliedCount).map(preparationService::prepare),
            )
            is MigrationVersionTraversalRequest.MigrateTo -> {
                val targetIndex = configuration.indexOf(request.target)
                if (targetIndex < appliedCount) {
                    throw IllegalStateException(
                        "Migration Version '${request.target.validatedValue}' has already been applied",
                    )
                }
                MigrationVersionTraversalPlan(
                    MigrationVersionTraversalDirection.UP,
                    configuration.registrations.subList(appliedCount, targetIndex + 1)
                        .map(preparationService::prepare),
                )
            }
            MigrationVersionTraversalRequest.RollbackLatest -> MigrationVersionTraversalPlan(
                MigrationVersionTraversalDirection.DOWN,
                history.appliedVersions.takeLast(1).asReversed(),
            )
            is MigrationVersionTraversalRequest.RollbackTo -> {
                val targetIndex = configuration.indexOf(request.target)
                if (targetIndex >= appliedCount) {
                    throw IllegalStateException(
                        "Migration Version '${request.target.validatedValue}' has not been applied",
                    )
                }
                MigrationVersionTraversalPlan(
                    MigrationVersionTraversalDirection.DOWN,
                    history.appliedVersions.drop(targetIndex + 1).asReversed(),
                )
            }
        }
    }

    private fun MigrationConfiguration.indexOf(target: MigrationVersionTag): Int {
        val targetName = target.validatedValue
        return registrations.indexOfFirst { it.name == targetName }
            .takeIf { it >= 0 }
            ?: throw IllegalArgumentException("Migration version '$targetName' is not registered")
    }
}
