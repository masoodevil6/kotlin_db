package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.model.migration_state.MigrationRecoveryTarget
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.model.version_selection.MigrationVersionSelectionDirection
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTransactionService
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionFlowOutcome
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionStateService
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTraversalDirection
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionTraversalService
import gog.my_project.data_base_migration.executor.service.version_selection.MigrationCurrentVersionService
import gog.my_project.data_base_migration.executor.service.version_selection.MigrationVersionSelectionService
import gog.my_project.data_base.core.events.DatabaseEventFailure
import gog.my_project.data_base_migration.core.events.MigrationCommand
import gog.my_project.data_base_migration.core.events.MigrationEvent
import gog.my_project.data_base_migration.core.events.MigrationEventType
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCapability
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
    private val stateService = MigrationVersionStateService(configuration)

    fun migrate(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = null, direction = MigrationVersionSelectionDirection.UP)

    fun migrate(version: MigrationVersionTag, scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = version, direction = MigrationVersionSelectionDirection.UP)

    fun rollback(scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = null, direction = MigrationVersionSelectionDirection.DOWN)

    fun rollback(version: MigrationVersionTag, scope: OperationExecutionScope): ExecuteResult<Boolean> =
        run(scope, target = version, direction = MigrationVersionSelectionDirection.DOWN)

    fun retryUp(
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
        scope: OperationExecutionScope,
    ): ExecuteResult<Boolean> = run(
        scope,
        target = null,
        direction = MigrationVersionSelectionDirection.UP,
        recoveryTarget = target,
        toCompleteVersion = toCompleteVersion,
    )

    fun retryDown(
        target: MigrationRecoveryTarget,
        toCompleteVersion: Boolean,
        scope: OperationExecutionScope,
    ): ExecuteResult<Boolean> = run(
        scope,
        target = null,
        direction = MigrationVersionSelectionDirection.DOWN,
        recoveryTarget = target,
        toCompleteVersion = toCompleteVersion,
    )

    private fun run(
        scope: OperationExecutionScope,
        target: MigrationVersionTag?,
        direction: MigrationVersionSelectionDirection,
        recoveryTarget: MigrationRecoveryTarget? = null,
        toCompleteVersion: Boolean = true,
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
            scope.withMigrationFailureStatusCapability(MIGRATION_LOCK_TIMEOUT_SECONDS) { capability ->
                runLocked(scope, target, direction, capability, recoveryTarget, toCompleteVersion)
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
        failureStatusCapability: MigrationFailureStatusCapability,
        recoveryTarget: MigrationRecoveryTarget? = null,
        toCompleteVersion: Boolean = true,
    ): ExecuteResult<Boolean> {
        return try {
            if (recoveryTarget != null) {
                return retryLocked(
                    scope,
                    recoveryTarget,
                    direction,
                    toCompleteVersion,
                    failureStatusCapability,
                ).throwIfFatalFailure(scope)
            }
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
                        failureStatusCapability,
                    )
                    MigrationVersionTraversalDirection.DOWN -> transactionService.transactionDown(
                        scope,
                        versionTag,
                        prepared,
                        preparedMigrations,
                        failureStatusCapability,
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

    /** Retry reuses this run's single lock/capability boundary and the shared Version service. */
    private fun retryLocked(
        scope: OperationExecutionScope,
        suppliedTarget: MigrationRecoveryTarget,
        direction: MigrationVersionSelectionDirection,
        toCompleteVersion: Boolean,
        failureStatusCapability: MigrationFailureStatusCapability,
    ): ExecuteResult<Boolean> {
        val validated = when (
            val result = stateService.validateTargetSnapshotWhileMigrationLockHeld(scope, suppliedTarget)
                .throwIfFatalFailure(scope)
        ) {
            is ExecuteResult.Failure -> {
                scope.recordResult(result)
                return result
            }
            is ExecuteResult.Success -> requireNotNull(result.result) {
                "Validated Migration Retry Target returned no snapshot"
            }
        }
        val expectedDirectionStatus = when (direction) {
            MigrationVersionSelectionDirection.UP ->
                gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState.STATUS_MIGRATE_STARTED
            MigrationVersionSelectionDirection.DOWN ->
                gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState.STATUS_ROLLBACK_STARTED
        }
        if (validated.checkpoint.originalStatus != expectedDirectionStatus) {
            return scope.failed<Boolean>(
                IllegalArgumentException("Retry Target direction does not match the requested operation"),
            ).throwIfFatalFailure(scope)
        }

        val prepared = validated.preparedVersion
        val versionTag = requireVersionTag(prepared)
        if (versionTag.validatedValue != prepared.version.name) {
            return scope.failed<Boolean>(
                IllegalArgumentException("Retry Version Tag does not match its Configuration registration"),
            ).throwIfFatalFailure(scope)
        }
        scope.publishDatabaseEvent(
            MigrationEvent(
                type = MigrationEventType.MIGRATION_VERSION_STARTED,
                command = when (direction) {
                    MigrationVersionSelectionDirection.UP -> MigrationCommand.MIGRATE
                    MigrationVersionSelectionDirection.DOWN -> MigrationCommand.ROLLBACK
                },
                versionName = prepared.version.name,
            ),
        )

        val outcome = when (direction) {
            MigrationVersionSelectionDirection.UP -> transactionService.retryUp(
                scope,
                validated,
                failureStatusCapability,
                toCompleteVersion,
            )
            MigrationVersionSelectionDirection.DOWN -> transactionService.retryDown(
                scope,
                validated,
                failureStatusCapability,
                toCompleteVersion,
            )
        }.throwIfFatalFailure(scope)

        return when (outcome) {
            is ExecuteResult.Failure -> {
                scope.recordResult(outcome)
                outcome
            }
            is ExecuteResult.Success -> when (requireNotNull(outcome.result)) {
                MigrationVersionFlowOutcome.Completed -> {
                    scope.publishDatabaseEvent(
                        MigrationEvent(
                            type = when (direction) {
                                MigrationVersionSelectionDirection.UP -> MigrationEventType.MIGRATION_VERSION_APPLIED
                                MigrationVersionSelectionDirection.DOWN -> MigrationEventType.MIGRATION_VERSION_ROLLED_BACK
                            },
                            command = when (direction) {
                                MigrationVersionSelectionDirection.UP -> MigrationCommand.MIGRATE
                                MigrationVersionSelectionDirection.DOWN -> MigrationCommand.ROLLBACK
                            },
                            versionName = prepared.version.name,
                        ),
                    )
                    ExecuteResult.Success(true)
                }
                MigrationVersionFlowOutcome.Paused -> ExecuteResult.Success(false)
            }
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
