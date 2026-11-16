package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTransactionService
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTraversalDirection
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTraversalService
import gog.my_project.data_base_migration.executor.service.version_selection.MigrationCurrentVersionService
import gog.my_project.data_base_migration.executor.service.version_selection.MigrationVersionSelectionService
import gog.my_project.data_base.core.events.DatabaseEventFailure
import gog.my_project.data_base_migration.core.events.MigrationCommand
import gog.my_project.data_base_migration.core.events.MigrationEvent
import gog.my_project.data_base_migration.core.events.MigrationEventType
import gog.my_project.data_base_operation.config.data_base.SystemLock
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionHistoryValidator
import model.prepared.PreparedMigrationVersion

/** Connects history validation, Version selection, traversal, and the existing per-Version transactions. */
internal class MigrationRunOrchestrationService(
    private val configuration: MigrationConfiguration,
) {
    private val currentVersionService = MigrationCurrentVersionService(configuration)
    private val historyValidator = MigrationVersionHistoryValidator()
    private val selectionService = MigrationVersionSelectionService(configuration)
    private val traversalService = MigrationVersionTraversalService()
    private val transactionService = MigrationVersionTransactionService(configuration)

    fun migrate(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = null, direction = MigrationVersionSelectionDirection.UP)

    fun migrate(version: MigrationVersionTag, scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = version, direction = MigrationVersionSelectionDirection.UP)

    fun rollback(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = null, direction = MigrationVersionSelectionDirection.DOWN)

    fun rollback(version: MigrationVersionTag, scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = version, direction = MigrationVersionSelectionDirection.DOWN)

    private fun run(
        scope: OperationExecutionScope,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
    ): ExecuteResult<Boolean> {
        scope.rejectOperation()?.let { failure ->
            if (failure is Error) {
                scope.recordFatal(failure)
                throw failure
            }
            return ExecuteResult.Failure(failure)
        }

        val command = when (direction) {
            MigrationVersionSelectionDirection.UP -> MigrationCommand.MIGRATE
            MigrationVersionSelectionDirection.DOWN -> MigrationCommand.ROLLBACK
        }
        val targetVersionName = target?.validatedValue
        scope.publishDatabaseEvent(
            MigrationEvent(
                type = MigrationEventType.MIGRATION_STARTED,
                command = command,
                targetVersionName = targetVersionName,
            ),
        )

        val result = try {
            scope.withSystemLock(SystemLock.MIGRATION, MIGRATION_LOCK_TIMEOUT_SECONDS) { lockedScope ->
                runLocked(lockedScope, target, direction)
            }.throwIfFatalFailure(scope)
        } catch (failure: Throwable) {
            scope.publishDatabaseEvent(
                MigrationEvent(
                    type = MigrationEventType.MIGRATION_FAILED,
                    command = command,
                    targetVersionName = targetVersionName,
                    failure = DatabaseEventFailure.from(failure),
                ),
            )
            throw failure
        }

        if (result is ExecuteResult.Failure) {
            scope.publishDatabaseEvent(
                MigrationEvent(
                    type = MigrationEventType.MIGRATION_FAILED,
                    command = command,
                    targetVersionName = targetVersionName,
                    failure = DatabaseEventFailure.from(result.exception),
                ),
            )
        } else {
            scope.publishDatabaseEvent(
                MigrationEvent(
                    type = MigrationEventType.MIGRATION_COMPLETED,
                    command = command,
                    targetVersionName = targetVersionName,
                ),
            )
        }
        return result
    }

    private fun runLocked(
        scope: OperationExecutionScope,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
    ): ExecuteResult<Boolean> {
        return try {
            val historyState = when (
                val historyResult = currentVersionService.validatedHistoryState(scope).throwIfFatalFailure(scope)
            ) {
                is ExecuteResult.Failure -> {
                    scope.recordResult(historyResult)
                    return historyResult
                }
                is ExecuteResult.Success -> requireNotNull(historyResult.result) {
                    "Validated Migration history returned no state"
                }
            }
            val current = historyState.currentVersion

            val selection = when (
                val selectionResult = selectionService.select(current, target, direction).throwIfFatalFailure(scope)
            ) {
                is ExecuteResult.Failure -> {
                    scope.recordResult(selectionResult)
                    return selectionResult
                }
                is ExecuteResult.Success -> requireNotNull(selectionResult.result) {
                    "Migration Version selection returned no result"
                }
            }

            val plan = traversalService.plan(selection)

            // Resolve every required tag before the first Version transaction so a later missing
            // tag cannot leave earlier Versions committed.
            val taggedVersions = plan.versions.map { prepared ->
                prepared to requireVersionTag(prepared)
            }

            when (plan.direction) {
                MigrationVersionTraversalDirection.UP -> historyValidator.validatePendingUpMigrations(
                    historyState.snapshot,
                    plan.versions,
                )
                MigrationVersionTraversalDirection.DOWN -> historyValidator.validateSelectedDownVersions(
                    historyState,
                    plan.versions,
                )
            }

            val preparedMigrationsByVersion = ArrayList<List<PreparedMigration>>(plan.versions.size)
            for (prepared in plan.versions) {
                when (
                    val preparationResult = transactionService.prepareMigrations(scope, prepared)
                        .throwIfFatalFailure(scope)
                ) {
                    is ExecuteResult.Failure -> {
                        scope.recordResult(preparationResult)
                        return preparationResult
                    }
                    is ExecuteResult.Success -> preparedMigrationsByVersion += requireNotNull(preparationResult.result) {
                        "Migration preparation returned no list for Version '${prepared.version.name}'"
                    }
                }
            }
            val executablePlan = plan.withPreparedMigrations(preparedMigrationsByVersion)

            for ((index, taggedVersion) in taggedVersions.withIndex()) {
                val versionTag = taggedVersion.second
                val prepared = executablePlan.versions[index]
                val preparedMigrations = requireNotNull(executablePlan.preparedMigrationsByVersion)[index]
                scope.publishDatabaseEvent(
                    MigrationEvent(
                        type = MigrationEventType.MIGRATION_VERSION_STARTED,
                        command = when (plan.direction) {
                            MigrationVersionTraversalDirection.UP -> MigrationCommand.MIGRATE
                            MigrationVersionTraversalDirection.DOWN -> MigrationCommand.ROLLBACK
                        },
                        versionName = prepared.version.name,
                    ),
                )
                val result = when (plan.direction) {
                    MigrationVersionTraversalDirection.UP -> transactionService.transactionUp(
                        scope,
                        versionTag,
                        prepared,
                        preparedMigrations,
                    )
                    MigrationVersionTraversalDirection.DOWN -> transactionService.transactionDown(
                        scope,
                        versionTag,
                        prepared,
                        preparedMigrations,
                    )
                }.throwIfFatalFailure(scope)
                when (result) {
                    is ExecuteResult.Failure -> {
                        scope.recordResult(result)
                        return result
                    }
                    is ExecuteResult.Success -> if (result.result != true) {
                        return scope.failed<Boolean>(
                            IllegalStateException(
                                "Migration Version '${prepared.version.name}' did not complete successfully",
                            ),
                        ).throwIfFatalFailure(scope)
                    } else {
                        scope.publishDatabaseEvent(
                            MigrationEvent(
                                type = when (plan.direction) {
                                    MigrationVersionTraversalDirection.UP -> MigrationEventType.MIGRATION_VERSION_APPLIED
                                    MigrationVersionTraversalDirection.DOWN -> MigrationEventType.MIGRATION_VERSION_ROLLED_BACK
                                },
                                command = when (plan.direction) {
                                    MigrationVersionTraversalDirection.UP -> MigrationCommand.MIGRATE
                                    MigrationVersionTraversalDirection.DOWN -> MigrationCommand.ROLLBACK
                                },
                                versionName = prepared.version.name,
                            ),
                        )
                    }
                }
            }

            ExecuteResult.Success(true)
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed<Boolean>(failure).throwIfFatalFailure(scope)
        }
    }

    private fun <T> ExecuteResult<T>.throwIfFatalFailure(
        scope: OperationExecutionScope,
    ): ExecuteResult<T> {
        val failure = (this as? ExecuteResult.Failure)?.exception
        if (failure is Error) {
            scope.recordFatal(failure)
            throw failure
        }
        return this
    }

    private fun requireVersionTag(prepared: PreparedMigrationVersion): MigrationVersionTag {
        val tag = prepared.version.tag
            ?: throw IllegalStateException(
                "Migration Version '${prepared.version.name}' has no valid MigrationVersionTag",
            )
        val tagName = tag.validatedValue
        require(tagName == prepared.version.name) {
            "Migration Version '${prepared.version.name}' is associated with MigrationVersionTag '$tagName'"
        }
        return tag
    }

    private companion object {
        /** A bounded one-minute wait allows ordinary contention without risking an indefinite call. */
        const val MIGRATION_LOCK_TIMEOUT_SECONDS = 60
    }
}
