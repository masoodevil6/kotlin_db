package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationVersionQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagInsertQuery
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.service.run.PreparedMigration
import gog.my_project.data_base_migration.executor.service.run.MigrationRunService
import gog.my_project.data_base_migration.executor.service.run.MigrationRunOutcome
import gog.my_project.data_base_migration.executor.service.result.MigrationExecutionResult
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnostic
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnosticStage
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureCheckpointWriteCapability
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureCheckpointWriteCommand
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCapability
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCommand
import gog.my_project.data_base_operation.migration.failure.spi.write
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.QueryRow
import model.version_history.MigrationVersionHistorySnapshot
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService
import java.sql.SQLException
import java.util.IdentityHashMap

/** Runs all Migrations belonging to one Version on the supplied Operation scope. */
internal class MigrationVersionTransactionService(
    private val configuration: MigrationConfiguration,
) {
    private val preparationService = MigrationVersionPreparationService()
    private val migrationRunService = MigrationRunService()
    private val historyReader = MigrationVersionHistoryReader()
    private val historyValidator = MigrationVersionHistoryValidator(preparationService)
    private val checkpointPersistence = MigrationVersionCheckpointPersistence()

    /** Applies one registered Version directly on the supplied Operation scope. */
    fun transactionUp(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): ExecuteResult<Boolean> = transactionUpInternal(scope, versionTag, null, null, null)

    /** Prepares runtime instances for a selected Version without writing Tags, History, or markers. */
    internal fun prepareMigrations(
        scope: OperationExecutionScope,
        selectedVersion: PreparedMigrationVersion,
    ): ExecuteResult<List<PreparedMigration>> {
        scope.rejectOperation()?.let { failure ->
            if (failure is Error) {
                scope.recordFatal(failure)
                throw failure
            }
            return ExecuteResult.Failure(failure)
        }

        val preparedMigrations = ArrayList<PreparedMigration>(selectedVersion.migrations.size)
        for (registration in selectedVersion.migrations) {
            when (val result = migrationRunService.prepareMigration(scope, registration)) {
                is ExecuteResult.Failure -> return result
                is ExecuteResult.Success -> preparedMigrations += requireNotNull(result.result) {
                    "Migration preparation returned no runtime instance"
                }
            }
        }
        return ExecuteResult.Success(preparedMigrations)
    }

    /** Applies a selected Version using instances prepared before the first Version marker. */
    internal fun transactionUp(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion,
        preparedMigrations: List<PreparedMigration>,
    ): ExecuteResult<Boolean> = transactionUpInternal(
        scope,
        versionTag,
        selectedVersion,
        preparedMigrations,
        null,
    )

    /** Applies a selected Version with the lock-scoped failure-status capability from orchestration. */
    internal fun transactionUp(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion,
        preparedMigrations: List<PreparedMigration>,
        failureStatusCapability: MigrationFailureStatusCapability,
    ): ExecuteResult<Boolean> = transactionUpInternal(
        scope,
        versionTag,
        selectedVersion,
        preparedMigrations,
        failureStatusCapability,
    )

    private fun transactionUpInternal(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion?,
        preparedMigrations: List<PreparedMigration>?,
        failureStatusCapability: MigrationFailureStatusCapability?,
    ): ExecuteResult<Boolean> = safely(scope) {
        val prepared = resolvePreparedVersion(scope, versionTag, selectedVersion, preparedMigrations)
            ?: return@safely failureResult(scope)

        val tagId = resolveVersionTagForUp(scope, versionTag)
            ?: return@safely failureResult(scope)

        val orderedMigrations = prepared.migrations
        var checkpoint = initialCheckpoint(
            MigrationVersionState.STATUS_MIGRATE_STARTED,
            orderedMigrations.firstOrNull()?.id?.value,
        )
        val initialCheckpointJson = MigrationRetryCheckpointCodec.encode(checkpoint)
        val versionId = checkpointPersistence.insertUpVersion(scope, tagId, initialCheckpointJson)
            .valueOrNull(scope)
            ?: return@safely failureResult(scope)
        when (val flow = runUpVersionFlow(
            scope = scope,
            versionTag = versionTag,
            versionId = versionId,
            orderedMigrations = orderedMigrations,
            preparedMigrations = preparedMigrations,
            startIndex = 0,
            initialCheckpoint = checkpoint,
            failureStatusCapability = failureStatusCapability,
            prepareBeforeSchemaBoundary = false,
            pauseAfterOneBoundary = false,
        )) {
            is ExecuteResult.Failure -> ExecuteResult.Failure(flow.exception)
            is ExecuteResult.Success -> when (requireNotNull(flow.result)) {
                MigrationVersionFlowOutcome.Completed -> ExecuteResult.Success(true)
                MigrationVersionFlowOutcome.Paused -> fail(scope, "Normal UP flow cannot pause")
            }
        }
    }

    /** Shared normal/Retry UP cursor flow; the recording-only branch cannot dispatch Schema. */
    private fun runUpVersionFlow(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        versionId: Int,
        orderedMigrations: List<RegisteredMigration>,
        preparedMigrations: List<PreparedMigration>?,
        startIndex: Int,
        initialCheckpoint: MigrationRetryCheckpoint,
        failureStatusCapability: MigrationFailureStatusCapability?,
        prepareBeforeSchemaBoundary: Boolean,
        pauseAfterOneBoundary: Boolean,
    ): ExecuteResult<MigrationVersionFlowOutcome> {
        var checkpoint = initialCheckpoint
        var index = startIndex
        var completedBoundaries = 0

        while (checkpoint.phase != MigrationRetryPhase.FINALIZATION_PENDING) {
            val migration = orderedMigrations.getOrNull(index)
                ?: return fail(scope, "UP Checkpoint cursor is outside the selected Version")
            if (checkpoint.cursorMigrationId != migration.id.value) {
                return fail(scope, "UP Checkpoint cursor does not match the selected Migration")
            }

            val runOutcome = when (checkpoint.phase) {
                MigrationRetryPhase.RECORD_PENDING -> migrationRunService.recordUpAttempt(
                    versionTag,
                    versionId,
                    scope,
                    migration,
                )
                MigrationRetryPhase.EXECUTION_PENDING,
                MigrationRetryPhase.PAUSED_NEXT,
                -> {
                    val runtimeMigration = when {
                        preparedMigrations != null -> preparedMigrations.getOrNull(index)
                            ?: return fail(scope, "Prepared UP Migration is missing for the Checkpoint cursor")
                        prepareBeforeSchemaBoundary -> migrationRunService.prepareMigration(scope, migration)
                            .valueOrNull(scope) ?: return failureResult(scope)
                        else -> null
                    }
                    val beforeSchema = {
                        val next = nextCheckpoint(
                            checkpoint,
                            checkpoint.cursorMigrationId,
                            MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                        )
                        persistCheckpointTransition(
                            scope,
                            versionId,
                            MigrationVersionState.STATUS_MIGRATE_STARTED,
                            checkpoint,
                            next,
                        ).also {
                            if (it is ExecuteResult.Success && it.result == true) checkpoint = next
                        }
                    }
                    val afterSchema = {
                        val next = nextCheckpoint(
                            checkpoint,
                            checkpoint.cursorMigrationId,
                            MigrationRetryPhase.RECORD_PENDING,
                        )
                        persistCheckpointTransition(
                            scope,
                            versionId,
                            MigrationVersionState.STATUS_MIGRATE_STARTED,
                            checkpoint,
                            next,
                        ).also {
                            if (it is ExecuteResult.Success && it.result == true) checkpoint = next
                        }
                    }
                    if (runtimeMigration != null) {
                        migrationRunService.runUpAttempt(
                            versionTag,
                            versionId,
                            scope,
                            runtimeMigration,
                            beforeSchema,
                            afterSchema,
                        )
                    } else {
                        migrationRunService.runUpAttempt(
                            versionTag,
                            versionId,
                            scope,
                            migration,
                            beforeSchema,
                            afterSchema,
                        )
                    }
                }
                else -> return fail(scope, "UP Checkpoint phase cannot enter the shared execution flow")
            }

            val result = when (runOutcome) {
                is MigrationRunOutcome.PreflightFailure -> runOutcome.toExecuteResult()
                is MigrationRunOutcome.Attempt -> when (val attempt = runOutcome.result) {
                    is MigrationExecutionResult.Failure -> {
                        recordUpAttemptFailure(
                            scope,
                            failureStatusCapability,
                            versionId,
                            attempt,
                            checkpoint,
                        )
                        ExecuteResult.Failure(attempt.cause)
                    }
                    is MigrationExecutionResult.Fatal -> throw attempt.error
                    is MigrationExecutionResult.Success -> ExecuteResult.Success(true)
                }
            }
            if (result is ExecuteResult.Failure) return result
            if ((result as ExecuteResult.Success).result != true) {
                return fail(scope, "Migration '${migration.id.value}' did not complete successfully")
            }

            val nextMigrationId = orderedMigrations.getOrNull(index + 1)?.id?.value
            val nextPhase = when {
                nextMigrationId == null -> MigrationRetryPhase.FINALIZATION_PENDING
                pauseAfterOneBoundary && completedBoundaries == 0 -> MigrationRetryPhase.PAUSED_NEXT
                else -> MigrationRetryPhase.EXECUTION_PENDING
            }
            val next = nextCheckpoint(checkpoint, nextMigrationId, nextPhase)
            val advanced = persistCheckpointTransition(
                scope,
                versionId,
                MigrationVersionState.STATUS_MIGRATE_STARTED,
                checkpoint,
                next,
            )
            if (advanced !is ExecuteResult.Success || advanced.result != true) {
                recordCheckpointFailure(
                    scope,
                    failureStatusCapability,
                    versionId,
                    checkpoint,
                    MigrationFailureStatusCommand.UpRecordingFailure(versionId),
                    expectedFailure = (advanced as? ExecuteResult.Failure)?.exception,
                ) { expected, updated, diagnostic ->
                    MigrationFailureCheckpointWriteCommand.UpRecordingFailure(
                        versionId,
                        expected,
                        updated,
                        diagnostic,
                    )
                }
                return failureResult(scope)
            }
            checkpoint = next
            completedBoundaries++
            if (pauseAfterOneBoundary && completedBoundaries == 1) {
                return ExecuteResult.Success(MigrationVersionFlowOutcome.Paused)
            }
            index++
        }

        return finalizeUpVersion(scope, versionTag, versionId, checkpoint, failureStatusCapability)
    }

    private fun finalizeUpVersion(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        versionId: Int,
        checkpoint: MigrationRetryCheckpoint,
        failureStatusCapability: MigrationFailureStatusCapability?,
    ): ExecuteResult<MigrationVersionFlowOutcome> {
        val finalized = checkpointPersistence.finalizeUp(
            scope,
            versionId,
            MigrationRetryCheckpointCodec.encode(checkpoint),
        ).valueOrNull(scope)
        if (finalized != MigrationCheckpointWriteResult.APPLIED) {
            if (scope.firstFailure == null) {
                scope.failed<Unit>(IllegalStateException(
                    "Could not finalize UP Migration Version '${versionTag.validatedValue}' from its Checkpoint",
                ))
            }
            recordCheckpointFailure(
                scope,
                failureStatusCapability,
                versionId,
                checkpoint,
                MigrationFailureStatusCommand.UpRecordingFailure(versionId),
                expectedFailure = scope.firstFailure,
            ) { expected, updated, diagnostic ->
                MigrationFailureCheckpointWriteCommand.UpFinalizationFailure(
                    versionId,
                    expected,
                    updated,
                    diagnostic,
                )
            }
            return failureResult(scope)
        }
        return ExecuteResult.Success(MigrationVersionFlowOutcome.Completed)
    }

    /** Resumes one validated UP Version through the same cursor flow used by normal execution. */
    internal fun retryUp(
        scope: OperationExecutionScope,
        validated: ValidatedMigrationRecoveryTarget,
        failureStatusCapability: MigrationFailureStatusCapability,
        toCompleteVersion: Boolean,
    ): ExecuteResult<MigrationVersionFlowOutcome> = safely(scope) {
        if (validated.status != MigrationVersionState.STATUS_MIGRATE_STARTED &&
            validated.status != MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD
        ) return@safely fail(scope, "Status ${validated.status} is not eligible for UP Retry")
        if (validated.checkpoint.originalStatus != MigrationVersionState.STATUS_MIGRATE_STARTED) {
            return@safely fail(scope, "UP Retry Checkpoint has a direction mismatch")
        }
        val versionTag = validated.preparedVersion.version.tag
            ?: return@safely fail(scope, "Retry Version has no registered MigrationVersionTag")
        if (versionTag.validatedValue != validated.preparedVersion.version.name) {
            return@safely fail(scope, "Retry Version Tag does not match its Configuration registration")
        }

        val orderedMigrations = validated.preparedVersion.migrations
        val checkpoint = validated.checkpoint
        val startIndex = when (checkpoint.phase) {
            MigrationRetryPhase.FINALIZATION_PENDING -> orderedMigrations.size
            MigrationRetryPhase.EXECUTION_PENDING,
            MigrationRetryPhase.PAUSED_NEXT,
            MigrationRetryPhase.EXECUTION_IN_FLIGHT,
            MigrationRetryPhase.RECORD_PENDING,
            -> orderedMigrations.indexOfFirst { it.id.value == checkpoint.cursorMigrationId }
                .takeIf { it >= 0 }
                ?: return@safely fail(scope, "UP Retry cursor is not registered in its Version")
        }

        if (validated.status == MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD &&
            !normalizeRecordingFailure(scope, validated, up = true)
        ) return@safely failureResult(scope)

        runUpVersionFlow(
            scope = scope,
            versionTag = versionTag,
            versionId = validated.target.version.versionId,
            orderedMigrations = orderedMigrations,
            preparedMigrations = null,
            startIndex = startIndex,
            initialCheckpoint = checkpoint,
            failureStatusCapability = failureStatusCapability,
            prepareBeforeSchemaBoundary = true,
            pauseAfterOneBoundary = !toCompleteVersion && checkpoint.phase != MigrationRetryPhase.FINALIZATION_PENDING,
        )
    }

    private fun normalizeRecordingFailure(
        scope: OperationExecutionScope,
        validated: ValidatedMigrationRecoveryTarget,
        up: Boolean,
    ): Boolean {
        val result = if (up) {
            checkpointPersistence.normalizeUpRecordingFailure(
                scope,
                validated.target.version.versionId,
                MigrationRetryCheckpointCodec.encode(validated.checkpoint),
            )
        } else {
            checkpointPersistence.normalizeDownRecordingFailure(
                scope,
                validated.target.version.versionId,
                MigrationRetryCheckpointCodec.encode(validated.checkpoint),
            )
        }
        val write = result.valueOrNull(scope) ?: return false
        if (write != MigrationCheckpointWriteResult.APPLIED) {
            scope.failed<Unit>(IllegalStateException(
                "Could not normalize ${if (up) "UP" else "DOWN"} recording-failure Status before Retry",
            ))
            return false
        }
        return true
    }

    private fun recordUpAttemptFailure(
        scope: OperationExecutionScope,
        capability: MigrationFailureStatusCapability?,
        versionId: Int,
        failure: MigrationExecutionResult.Failure,
        checkpoint: MigrationRetryCheckpoint,
    ) {
        if (failure.direction != MigrationExecutionResult.Direction.UP ||
            failure.versionRecordId != versionId
        ) return

        val (diagnosticCommand, buildCheckpointCommand) = when {
            failure.stage == MigrationExecutionResult.FailureStage.RECONCILIATION ->
                MigrationFailureStatusCommand.UpRecordingFailure(versionId) to
                    { expected: String, updated: String, diagnostic: String ->
                        MigrationFailureCheckpointWriteCommand.UpReconciliationInconsistent(
                            versionId,
                            expected,
                            updated,
                            diagnostic,
                        )
                    }
            checkpoint.phase == MigrationRetryPhase.RECORD_PENDING ||
                checkpoint.phase == MigrationRetryPhase.FINALIZATION_PENDING ->
                MigrationFailureStatusCommand.UpRecordingFailure(versionId) to
                    { expected: String, updated: String, diagnostic: String ->
                        MigrationFailureCheckpointWriteCommand.UpRecordingFailure(
                            versionId,
                            expected,
                            updated,
                            diagnostic,
                        )
                    }
            else -> MigrationFailureStatusCommand.UpExecutionFailure(versionId) to
                { expected: String, updated: String, diagnostic: String ->
                    MigrationFailureCheckpointWriteCommand.UpExecutionUncertain(
                        versionId,
                        expected,
                        updated,
                        diagnostic,
                    )
                }
        }
        recordCheckpointFailure(
            scope,
            capability,
            versionId,
            checkpoint,
            diagnosticCommand,
            expectedFailure = failure.cause,
            migrationId = failure.migrationId?.value,
            buildCheckpointCommand = buildCheckpointCommand,
        )
    }

    private fun recordDownAttemptFailure(
        scope: OperationExecutionScope,
        capability: MigrationFailureStatusCapability?,
        versionId: Int,
        failure: MigrationExecutionResult.Failure,
        checkpoint: MigrationRetryCheckpoint,
    ) {
        if (failure.direction != MigrationExecutionResult.Direction.DOWN ||
            failure.versionRecordId != versionId
        ) return

        val (diagnosticCommand, buildCheckpointCommand) = when {
            failure.stage == MigrationExecutionResult.FailureStage.RECONCILIATION ->
                MigrationFailureStatusCommand.DownRecordingFailure(versionId) to
                    { expected: String, updated: String, diagnostic: String ->
                        MigrationFailureCheckpointWriteCommand.DownReconciliationInconsistent(
                            versionId,
                            expected,
                            updated,
                            diagnostic,
                        )
                    }
            checkpoint.phase == MigrationRetryPhase.RECORD_PENDING ||
                checkpoint.phase == MigrationRetryPhase.FINALIZATION_PENDING ->
                MigrationFailureStatusCommand.DownRecordingFailure(versionId) to
                    { expected: String, updated: String, diagnostic: String ->
                        MigrationFailureCheckpointWriteCommand.DownRecordingFailure(
                            versionId,
                            expected,
                            updated,
                            diagnostic,
                        )
                    }
            else -> MigrationFailureStatusCommand.DownExecutionFailure(versionId) to
                { expected: String, updated: String, diagnostic: String ->
                    MigrationFailureCheckpointWriteCommand.DownExecutionUncertain(
                        versionId,
                        expected,
                        updated,
                        diagnostic,
                    )
                }
        }
        recordCheckpointFailure(
            scope,
            capability,
            versionId,
            checkpoint,
            diagnosticCommand,
            expectedFailure = failure.cause,
            migrationId = failure.migrationId?.value,
            buildCheckpointCommand = buildCheckpointCommand,
        )
    }

    /**
     * Uses the existing one-shot capability after Operation has latched the Failure. The Checkpoint
     * and Diagnostic are supplied together, while Operation remains agnostic to their JSON content.
     */
    private fun recordCheckpointFailure(
        scope: OperationExecutionScope,
        capability: MigrationFailureStatusCapability?,
        versionId: Int,
        checkpoint: MigrationRetryCheckpoint,
        diagnosticCommand: MigrationFailureStatusCommand,
        expectedFailure: Throwable? = null,
        migrationId: String? = null,
        buildCheckpointCommand: (String, String, String) -> MigrationFailureCheckpointWriteCommand,
    ) {
        if (capability == null || versionId <= 0 || scope.fatalError != null) return
        val primaryFailure = scope.firstFailure ?: return
        if (primaryFailure is Error || (expectedFailure != null && primaryFailure !== expectedFailure)) return

        val diagnostic = failureDiagnosticSafely(
            scope,
            diagnosticCommand,
            expectedFailure ?: primaryFailure,
            migrationId,
        )
        val diagnosticMessage = try {
            diagnostic?.toStorageMessage(versionId)
        } catch (fatal: Error) {
            propagateDiagnosticFatal(scope, fatal)
        } catch (serializationFailure: Throwable) {
            recordDiagnosticConstructionFailure(scope, serializationFailure)
            null
        }
        if (diagnostic == null || diagnosticMessage == null) {
            capability.write(diagnosticCommand)
            return
        }

        val checkpointCapability = capability as? MigrationFailureCheckpointWriteCapability
        if (checkpointCapability == null) {
            // Older/custom capability implementations retain their status-only contract. The
            // Operation implementation used by managed execution supports the atomic Checkpoint write.
            capability.write(diagnosticCommand, diagnostic)
            return
        }

        val command = try {
            val expectedCheckpoint = MigrationRetryCheckpointCodec.encode(checkpoint)
            val updatedCheckpoint = MigrationRetryCheckpointCodec.encode(
                checkpoint.copy(
                    revision = MigrationRetryCheckpointCodec.nextRevision(checkpoint),
                    originalMessage = checkpoint.originalMessage ?: diagnosticMessage,
                ),
            )
            buildCheckpointCommand(expectedCheckpoint, updatedCheckpoint, diagnosticMessage)
        } catch (fatal: Error) {
            propagateDiagnosticFatal(scope, fatal)
        } catch (constructionFailure: Throwable) {
            recordDiagnosticConstructionFailure(scope, constructionFailure)
            capability.write(diagnosticCommand, diagnostic)
            return
        }
        checkpointCapability.writeCheckpoint(command)
    }

    private fun initialCheckpoint(
        originalStatus: Int,
        firstMigrationId: String?,
    ): MigrationRetryCheckpoint = MigrationRetryCheckpoint(
        formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
        revision = 0,
        originalStatus = originalStatus,
        originalMessage = null,
        cursorMigrationId = firstMigrationId,
        phase = if (firstMigrationId == null) {
            MigrationRetryPhase.FINALIZATION_PENDING
        } else {
            MigrationRetryPhase.EXECUTION_PENDING
        },
    )

    private fun nextCheckpoint(
        current: MigrationRetryCheckpoint,
        cursorMigrationId: String?,
        phase: MigrationRetryPhase,
    ): MigrationRetryCheckpoint = current.copy(
        revision = MigrationRetryCheckpointCodec.nextRevision(current),
        cursorMigrationId = cursorMigrationId,
        phase = phase,
    )

    /** Persists one expected Checkpoint transition; any non-APPLIED CAS is terminal for this run. */
    private fun persistCheckpointTransition(
        scope: OperationExecutionScope,
        versionId: Int,
        expectedStatus: Int,
        expected: MigrationRetryCheckpoint,
        updated: MigrationRetryCheckpoint,
    ): ExecuteResult<Boolean> = when (
        val result = checkpointPersistence.advanceCheckpoint(
            scope,
            versionId,
            expectedStatus,
            MigrationRetryCheckpointCodec.encode(expected),
            MigrationRetryCheckpointCodec.encode(updated),
        )
    ) {
        is ExecuteResult.Failure -> {
            scope.recordResult(result)
            ExecuteResult.Failure(scope.firstFailure ?: result.exception)
        }
        is ExecuteResult.Success -> when (result.result) {
            MigrationCheckpointWriteResult.APPLIED -> ExecuteResult.Success(true)
            MigrationCheckpointWriteResult.NOT_APPLIED -> scope.failed(
                IllegalStateException("Migration Checkpoint compare-and-set was not applied"),
            )
            MigrationCheckpointWriteResult.INDETERMINATE -> scope.failed(
                IllegalStateException("Migration Checkpoint compare-and-set outcome is indeterminate"),
            )
            null -> scope.failed(IllegalStateException("Migration Checkpoint compare-and-set returned no result"))
        }
    }

    /**
     * Prepares and validates the complete JSON before consuming the one-shot capability. If
     * throwable metadata is unavailable, record that problem as secondary and use safe metadata.
     * If even the safe representation cannot be produced, use the existing status-only write.
     */
    private fun failureDiagnosticSafely(
        scope: OperationExecutionScope,
        command: MigrationFailureStatusCommand,
        failure: Throwable,
        migrationId: String?,
    ): MigrationFailureDiagnostic? {
        try {
            return failureDiagnostic(command, failure, migrationId).also {
                // Validate encoding before the single status attempt. The Operation implementation
                // serializes the immutable DTO again while preparing the same UPDATE.
                it.toStorageMessage(command.versionId)
            }
        } catch (fatal: Error) {
            propagateDiagnosticFatal(scope, fatal)
        } catch (diagnosticFailure: Throwable) {
            recordDiagnosticConstructionFailure(scope, diagnosticFailure)
        }

        return try {
            fallbackFailureDiagnostic(command, failure, migrationId).also {
                it.toStorageMessage(command.versionId)
            }
        } catch (fatal: Error) {
            propagateDiagnosticFatal(scope, fatal)
        } catch (fallbackFailure: Throwable) {
            recordDiagnosticConstructionFailure(scope, fallbackFailure)
            null
        }
    }

    private fun recordDiagnosticConstructionFailure(
        scope: OperationExecutionScope,
        failure: Throwable,
    ) {
        try {
            scope.recordFailure(failure)
        } catch (fatal: Error) {
            propagateDiagnosticFatal(scope, fatal)
        }
    }

    private fun propagateDiagnosticFatal(
        scope: OperationExecutionScope,
        fatal: Error,
    ): Nothing {
        scope.recordFatal(fatal)
        throw scope.fatalError ?: fatal
    }

    private fun fallbackFailureDiagnostic(
        command: MigrationFailureStatusCommand,
        failure: Throwable,
        migrationId: String?,
    ): MigrationFailureDiagnostic = MigrationFailureDiagnostic(
        stage = diagnosticStage(command),
        // javaClass does not call Throwable message/cause or SQLException metadata accessors.
        exceptionClassName = failure.javaClass.name,
        sqlState = null,
        vendorErrorCode = null,
        message = null,
        migrationId = migrationId,
    )

    private fun failureDiagnostic(
        command: MigrationFailureStatusCommand,
        failure: Throwable,
        migrationId: String?,
    ): MigrationFailureDiagnostic {
        val sqlFailure = findSqlException(failure)
        return MigrationFailureDiagnostic(
            stage = diagnosticStage(command),
            exceptionClassName = failure.javaClass.name,
            sqlState = sqlFailure?.sqlState,
            vendorErrorCode = sqlFailure?.errorCode,
            message = failure.message ?: sqlFailure?.message,
            migrationId = migrationId,
        )
    }

    private fun diagnosticStage(
        command: MigrationFailureStatusCommand,
    ): MigrationFailureDiagnosticStage = when (command) {
        is MigrationFailureStatusCommand.UpExecutionFailure -> MigrationFailureDiagnosticStage.UP_EXECUTION
        is MigrationFailureStatusCommand.UpRecordingFailure -> MigrationFailureDiagnosticStage.UP_RECORDING
        is MigrationFailureStatusCommand.DownExecutionFailure -> MigrationFailureDiagnosticStage.DOWN_EXECUTION
        is MigrationFailureStatusCommand.DownRecordingFailure -> MigrationFailureDiagnosticStage.DOWN_RECORDING
    }

    private fun findSqlException(failure: Throwable): SQLException? {
        val seen = IdentityHashMap<Throwable, Boolean>()
        var current: Throwable? = failure
        while (current != null && seen.put(current, true) == null) {
            if (current is SQLException) return current
            current = current.cause
        }
        return null
    }

    /** Rolls back one applied Version directly on the supplied Operation scope, preserving reverse order. */
    fun transactionDown(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): ExecuteResult<Boolean> = transactionDownInternal(scope, versionTag, null, null, null)

    /** Rolls back a selected Version using instances prepared before the first Version marker. */
    internal fun transactionDown(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion,
        preparedMigrations: List<PreparedMigration>,
    ): ExecuteResult<Boolean> = transactionDownInternal(
        scope,
        versionTag,
        selectedVersion,
        preparedMigrations,
        null,
    )

    /** Rolls back a selected Version with the lock-scoped failure-status capability from orchestration. */
    internal fun transactionDown(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion,
        preparedMigrations: List<PreparedMigration>,
        failureStatusCapability: MigrationFailureStatusCapability,
    ): ExecuteResult<Boolean> = transactionDownInternal(
        scope,
        versionTag,
        selectedVersion,
        preparedMigrations,
        failureStatusCapability,
    )

    private fun transactionDownInternal(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion?,
        preparedMigrations: List<PreparedMigration>?,
        failureStatusCapability: MigrationFailureStatusCapability?,
    ): ExecuteResult<Boolean> = safely(scope) {
        val prepared = resolvePreparedVersion(scope, versionTag, selectedVersion, preparedMigrations)
            ?: return@safely failureResult(scope)

        val snapshot = when (val result = historyReader.read(scope)) {
            is ExecuteResult.Failure -> {
                scope.recordResult(result)
                return@safely failureResult(scope)
            }
            is ExecuteResult.Success -> requireNotNull(result.result)
        }
        val matchingTags = snapshot.tags.filter {
            it.name == versionTag.validatedValue && it.type == MigrationTagType.VERSION.code
        }
        if (matchingTags.size != 1) {
            return@safely fail(
                scope,
                if (matchingTags.isEmpty()) {
                    "Version Tag '${versionTag.validatedValue}' is not registered"
                } else {
                    "Duplicate Version Tags exist for '${versionTag.validatedValue}'"
                }
            )
        }
        val tagId = matchingTags.single().id
        val persistedVersions = snapshot.versions.filter { it.tagId == tagId }
        if (persistedVersions.isEmpty()) {
            return@safely fail(
                scope,
                "Migration Version '${versionTag.validatedValue}' has not been applied",
            )
        }
        if (persistedVersions.size != 1) {
            return@safely fail(
                scope,
                "Duplicate persisted Migration Versions exist for '${versionTag.validatedValue}'",
            )
        }
        val persistedVersion = persistedVersions.single()
        if (persistedVersion.tagId != tagId) {
            return@safely fail(
                scope,
                "Persisted Migration Version '${versionTag.validatedValue}' resolved to an unexpected Tag",
            )
        }
        val versionId = persistedVersion.id

        validateHistory(scope, snapshot, versionId, prepared.migrations)
            ?.let { return@safely it }

        val orderedMigrations = prepared.migrations.indices.reversed().map { index ->
            index to prepared.migrations[index]
        }
        var checkpoint = initialCheckpoint(
            MigrationVersionState.STATUS_ROLLBACK_STARTED,
            orderedMigrations.firstOrNull()?.second?.id?.value,
        )
        val initialCheckpointJson = MigrationRetryCheckpointCodec.encode(checkpoint)
        val started = checkpointPersistence.startDown(scope, versionId, initialCheckpointJson)
            .valueOrNull(scope)
        if (started != MigrationCheckpointWriteResult.APPLIED) {
            if (scope.firstFailure == null) {
                scope.failed<Unit>(IllegalStateException(
                    "Could not establish DOWN Checkpoint for Migration Version '${versionTag.validatedValue}'",
                ))
            }
            return@safely failureResult(scope)
        }

        when (val flow = runDownVersionFlow(
            scope = scope,
            versionTag = versionTag,
            versionId = versionId,
            orderedMigrations = orderedMigrations,
            preparedMigrations = preparedMigrations,
            startIndex = 0,
            initialCheckpoint = checkpoint,
            failureStatusCapability = failureStatusCapability,
            prepareBeforeSchemaBoundary = false,
            pauseAfterOneBoundary = false,
        )) {
            is ExecuteResult.Failure -> ExecuteResult.Failure(flow.exception)
            is ExecuteResult.Success -> when (requireNotNull(flow.result)) {
                MigrationVersionFlowOutcome.Completed -> ExecuteResult.Success(true)
                MigrationVersionFlowOutcome.Paused -> fail(scope, "Normal DOWN flow cannot pause")
            }
        }
    }

    /** Shared normal/Retry DOWN cursor flow; RECORD_PENDING uses only the metadata reconciler. */
    private fun runDownVersionFlow(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        versionId: Int,
        orderedMigrations: List<Pair<Int, RegisteredMigration>>,
        preparedMigrations: List<PreparedMigration>?,
        startIndex: Int,
        initialCheckpoint: MigrationRetryCheckpoint,
        failureStatusCapability: MigrationFailureStatusCapability?,
        prepareBeforeSchemaBoundary: Boolean,
        pauseAfterOneBoundary: Boolean,
    ): ExecuteResult<MigrationVersionFlowOutcome> {
        var checkpoint = initialCheckpoint
        var position = startIndex
        var completedBoundaries = 0

        while (checkpoint.phase != MigrationRetryPhase.FINALIZATION_PENDING) {
            val indexedMigration = orderedMigrations.getOrNull(position)
                ?: return fail(scope, "DOWN Checkpoint cursor is outside the selected Version")
            val (index, migration) = indexedMigration
            if (checkpoint.cursorMigrationId != migration.id.value) {
                return fail(scope, "DOWN Checkpoint cursor does not match the selected Migration")
            }

            val runOutcome = when (checkpoint.phase) {
                MigrationRetryPhase.RECORD_PENDING -> migrationRunService.reconcileDownRecording(
                    versionTag,
                    versionId,
                    scope,
                    migration,
                )
                MigrationRetryPhase.EXECUTION_PENDING,
                MigrationRetryPhase.PAUSED_NEXT,
                -> {
                    val runtimeMigration = when {
                        preparedMigrations != null -> preparedMigrations.getOrNull(index)
                            ?: return fail(scope, "Prepared DOWN Migration is missing for the Checkpoint cursor")
                        prepareBeforeSchemaBoundary -> migrationRunService.prepareMigration(scope, migration)
                            .valueOrNull(scope) ?: return failureResult(scope)
                        else -> null
                    }
                    val beforeSchema = {
                        val next = nextCheckpoint(
                            checkpoint,
                            checkpoint.cursorMigrationId,
                            MigrationRetryPhase.EXECUTION_IN_FLIGHT,
                        )
                        persistCheckpointTransition(
                            scope,
                            versionId,
                            STATUS_ROLLBACK_STARTED,
                            checkpoint,
                            next,
                        ).also {
                            if (it is ExecuteResult.Success && it.result == true) checkpoint = next
                        }
                    }
                    val afterSchema = {
                        val next = nextCheckpoint(
                            checkpoint,
                            checkpoint.cursorMigrationId,
                            MigrationRetryPhase.RECORD_PENDING,
                        )
                        persistCheckpointTransition(
                            scope,
                            versionId,
                            STATUS_ROLLBACK_STARTED,
                            checkpoint,
                            next,
                        ).also {
                            if (it is ExecuteResult.Success && it.result == true) checkpoint = next
                        }
                    }
                    if (runtimeMigration != null) {
                        migrationRunService.runDownAttempt(
                            versionTag,
                            versionId,
                            scope,
                            runtimeMigration,
                            beforeSchema,
                            afterSchema,
                        )
                    } else {
                        migrationRunService.runDownAttempt(
                            versionTag,
                            versionId,
                            scope,
                            migration,
                            beforeSchema,
                            afterSchema,
                        )
                    }
                }
                else -> return fail(scope, "DOWN Checkpoint phase cannot enter the shared execution flow")
            }

            val result = when (runOutcome) {
                is MigrationRunOutcome.PreflightFailure -> runOutcome.toExecuteResult()
                is MigrationRunOutcome.Attempt -> when (val attempt = runOutcome.result) {
                    is MigrationExecutionResult.Failure -> {
                        recordDownAttemptFailure(
                            scope,
                            failureStatusCapability,
                            versionId,
                            attempt,
                            checkpoint,
                        )
                        ExecuteResult.Failure(attempt.cause)
                    }
                    is MigrationExecutionResult.Fatal -> throw attempt.error
                    is MigrationExecutionResult.Success -> ExecuteResult.Success(true)
                }
            }
            if (result is ExecuteResult.Failure) return result
            if ((result as ExecuteResult.Success).result != true) {
                return fail(scope, "Migration '${migration.id.value}' did not roll back successfully")
            }

            val nextMigrationId = orderedMigrations.getOrNull(position + 1)?.second?.id?.value
            val nextPhase = when {
                nextMigrationId == null -> MigrationRetryPhase.FINALIZATION_PENDING
                pauseAfterOneBoundary && completedBoundaries == 0 -> MigrationRetryPhase.PAUSED_NEXT
                else -> MigrationRetryPhase.EXECUTION_PENDING
            }
            val next = nextCheckpoint(checkpoint, nextMigrationId, nextPhase)
            val advanced = persistCheckpointTransition(
                scope,
                versionId,
                STATUS_ROLLBACK_STARTED,
                checkpoint,
                next,
            )
            if (advanced !is ExecuteResult.Success || advanced.result != true) {
                recordCheckpointFailure(
                    scope,
                    failureStatusCapability,
                    versionId,
                    checkpoint,
                    MigrationFailureStatusCommand.DownRecordingFailure(versionId),
                    expectedFailure = (advanced as? ExecuteResult.Failure)?.exception,
                ) { expected, updated, diagnostic ->
                    MigrationFailureCheckpointWriteCommand.DownRecordingFailure(
                        versionId,
                        expected,
                        updated,
                        diagnostic,
                    )
                }
                return failureResult(scope)
            }
            checkpoint = next
            completedBoundaries++
            if (pauseAfterOneBoundary && completedBoundaries == 1) {
                return ExecuteResult.Success(MigrationVersionFlowOutcome.Paused)
            }
            position++
        }

        return finalizeDownVersion(scope, versionTag, versionId, checkpoint, failureStatusCapability)
    }

    private fun finalizeDownVersion(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        versionId: Int,
        checkpoint: MigrationRetryCheckpoint,
        failureStatusCapability: MigrationFailureStatusCapability?,
    ): ExecuteResult<MigrationVersionFlowOutcome> {
        val finalized = checkpointPersistence.finalizeDown(
            scope,
            versionId,
            MigrationRetryCheckpointCodec.encode(checkpoint),
        ).valueOrNull(scope)
        if (finalized != MigrationCheckpointWriteResult.APPLIED) {
            if (scope.firstFailure == null) {
                scope.failed<Unit>(IllegalStateException(
                    "Could not finalize DOWN Migration Version '${versionTag.validatedValue}' from its Checkpoint",
                ))
            }
            recordCheckpointFailure(
                scope,
                failureStatusCapability,
                versionId,
                checkpoint,
                MigrationFailureStatusCommand.DownRecordingFailure(versionId),
                expectedFailure = scope.firstFailure,
            ) { expected, updated, diagnostic ->
                MigrationFailureCheckpointWriteCommand.DownFinalizationFailure(
                    versionId,
                    expected,
                    updated,
                    diagnostic,
                )
            }
            return failureResult(scope)
        }
        return ExecuteResult.Success(MigrationVersionFlowOutcome.Completed)
    }

    /** Resumes one validated DOWN Version through the same reverse cursor flow as normal rollback. */
    internal fun retryDown(
        scope: OperationExecutionScope,
        validated: ValidatedMigrationRecoveryTarget,
        failureStatusCapability: MigrationFailureStatusCapability,
        toCompleteVersion: Boolean,
    ): ExecuteResult<MigrationVersionFlowOutcome> = safely(scope) {
        if (validated.status != MigrationVersionState.STATUS_ROLLBACK_STARTED &&
            validated.status != MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD
        ) return@safely fail(scope, "Status ${validated.status} is not eligible for DOWN Retry")
        if (validated.checkpoint.originalStatus != MigrationVersionState.STATUS_ROLLBACK_STARTED) {
            return@safely fail(scope, "DOWN Retry Checkpoint has a direction mismatch")
        }
        val versionTag = validated.preparedVersion.version.tag
            ?: return@safely fail(scope, "Retry Version has no registered MigrationVersionTag")
        if (versionTag.validatedValue != validated.preparedVersion.version.name) {
            return@safely fail(scope, "Retry Version Tag does not match its Configuration registration")
        }

        val orderedMigrations = validated.preparedVersion.migrations.indices.reversed().map { index ->
            index to validated.preparedVersion.migrations[index]
        }
        val checkpoint = validated.checkpoint
        val startIndex = when (checkpoint.phase) {
            MigrationRetryPhase.FINALIZATION_PENDING -> orderedMigrations.size
            MigrationRetryPhase.EXECUTION_PENDING,
            MigrationRetryPhase.PAUSED_NEXT,
            MigrationRetryPhase.EXECUTION_IN_FLIGHT,
            MigrationRetryPhase.RECORD_PENDING,
            -> orderedMigrations.indexOfFirst { it.second.id.value == checkpoint.cursorMigrationId }
                .takeIf { it >= 0 }
                ?: return@safely fail(scope, "DOWN Retry cursor is not registered in its Version")
        }

        if (validated.status == MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD &&
            !normalizeRecordingFailure(scope, validated, up = false)
        ) return@safely failureResult(scope)

        runDownVersionFlow(
            scope = scope,
            versionTag = versionTag,
            versionId = validated.target.version.versionId,
            orderedMigrations = orderedMigrations,
            preparedMigrations = null,
            startIndex = startIndex,
            initialCheckpoint = checkpoint,
            failureStatusCapability = failureStatusCapability,
            prepareBeforeSchemaBoundary = true,
            pauseAfterOneBoundary = !toCompleteVersion && checkpoint.phase != MigrationRetryPhase.FINALIZATION_PENDING,
        )
    }

    private fun <T> safely(
        scope: OperationExecutionScope,
        block: () -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        scope.rejectOperation()?.let { failure ->
            if (failure is Error) {
                scope.recordFatal(failure)
                throw failure
            }
            return ExecuteResult.Failure(failure)
        }
        return try {
            val result = block()
            val failure = (result as? ExecuteResult.Failure)?.exception
            if (failure is Error) {
                scope.recordFatal(failure)
                throw failure
            }
            result.also(scope::recordResult)
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            val result = scope.failed<T>(failure)
            val recordedFailure = (result as? ExecuteResult.Failure)?.exception
            if (recordedFailure is Error) {
                scope.recordFatal(recordedFailure)
                throw recordedFailure
            }
            result
        }
    }

    private fun prepare(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): PreparedMigrationVersion? = try {
        preparationService.prepare(configuration, versionTag)
    } catch (failure: Exception) {
        scope.failed<PreparedMigrationVersion>(failure)
        null
    }

    private fun resolvePreparedVersion(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
        selectedVersion: PreparedMigrationVersion?,
        preparedMigrations: List<PreparedMigration>?,
    ): PreparedMigrationVersion? {
        if (selectedVersion == null && preparedMigrations == null) {
            return prepare(scope, versionTag)
        }
        if (selectedVersion == null || preparedMigrations == null) {
            fail(scope, "Prepared Migration Version and runtime instances must be supplied together")
            return null
        }
        if (selectedVersion.version.name != versionTag.validatedValue) {
            fail(
                scope,
                "Prepared Migration Version '${selectedVersion.version.name}' does not match " +
                    "'${versionTag.validatedValue}'",
            )
            return null
        }
        if (preparedMigrations.size != selectedVersion.migrations.size ||
            preparedMigrations.indices.any { index ->
                preparedMigrations[index].registration !== selectedVersion.migrations[index]
            }
        ) {
            fail(
                scope,
                "Prepared Migration instances do not match the registrations for " +
                    "Version '${selectedVersion.version.name}'",
            )
            return null
        }
        return selectedVersion
    }

    private fun resolveVersionTagForUp(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): Int? {
        val existing = readVersionTags(scope, versionTag) ?: return null
        if (existing.size > 1) {
            fail(scope, "Duplicate Version Tags exist for '${versionTag.validatedValue}'")
            return null
        }
        existing.singleOrNull()?.let { tag ->
            val versions = readVersions(scope, tag.id) ?: return null
            when (versions.size) {
                0 -> return tag.id
                1 -> {
                    fail(scope, "Migration Version '${versionTag.validatedValue}' is already applied")
                    return null
                }
                else -> {
                    fail(
                        scope,
                        "Persisted Migration history is inconsistent: found ${versions.size} Versions " +
                            "for Tag '${versionTag.validatedValue}'",
                    )
                    return null
                }
            }
        }

        return insertVersionTag(scope, versionTag)
    }

    private fun insertVersionTag(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): Int? {
        val inserted = executeInsert(
            scope,
            SystemMigrationTagInsertQuery(
                versionTag.validatedValue,
                MigrationTagType.VERSION.code,
            ).query(),
        ) ?: return null
        return inserted.toPositiveIntId(scope, "Generated Version Tag id")
    }

    /** Checks the complete persisted history before the first Migration down operation runs. */
    private fun validateHistory(
        scope: OperationExecutionScope,
        snapshot: MigrationVersionHistorySnapshot,
        versionId: Int,
        migrations: List<RegisteredMigration>,
    ): ExecuteResult.Failure? {
        return try {
            historyValidator.validateVersion(snapshot, versionId, migrations)
            null
        } catch (failure: Exception) {
            fail(scope, failure.message ?: "Persisted Migration history is inconsistent")
        }
    }

    private fun readVersionTags(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): List<VersionTagRow>? = read(scope, SystemMigrationTagQuery().createCte(
        SystemMigrationTagQuery.RelationFilters(
            tagName = versionTag.validatedValue,
            tagType = MigrationTagType.VERSION.code,
        ),
    ).definition.ast) { row ->
        VersionTagRow(id = row.requiredInt("id", "Version Tag query returned a null id"))
    }.valueOrNull(scope)

    private fun readVersions(scope: OperationExecutionScope, tagId: Int): List<VersionRow>? =
        read(scope, SystemMigrationVersionQuery().createCte(
            SystemMigrationVersionQuery.RelationFilters(tagId = tagId),
        ).definition.ast) { row ->
            VersionRow(
                id = row.requiredInt("id", "Version query returned a null id"),
                tagId = row.requiredInt("tagId", "Version query returned a null tagId"),
            )
        }.valueOrNull(scope)

    private fun <T> read(
        scope: OperationExecutionScope,
        ast: gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst,
        mapper: (QueryRow) -> T,
    ): ExecuteResult<List<T>> {
        val result = QueryBuilderExecutor.scopeBound(scope)
            .queryBuilder(QueryRenderSelectBuilder(ast = ast))
            .execute()

        return when (result) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> try {
                ExecuteResult.Success(
                    requireNotNull(result.result) { "System Migration lookup returned no rows" }.map(mapper),
                )
            } catch (error: Error) {
                scope.recordFatal(error)
                throw error
            } catch (failure: Throwable) {
                scope.failed(failure)
            }
        }
    }

    private fun QueryRow.requiredInt(alias: String, failureMessage: String): Int =
        requireNotNull(this[alias] as? Int) { failureMessage }

    private fun executeInsert(
        scope: OperationExecutionScope,
        query: IQueryRenderInsertApi,
    ): Long? = QueryBuilderExecutor.scopeBound(scope)
        .queryBuilder(query)
        .execute()
        .valueOrNull(scope)

    private fun Long.toPositiveIntId(scope: OperationExecutionScope, description: String): Int? {
        if (this !in 1..Int.MAX_VALUE.toLong()) {
            fail(scope, "$description $this is outside the supported positive Int range")
            return null
        }
        return toInt()
    }

    private fun fail(scope: OperationExecutionScope, message: String): ExecuteResult.Failure {
        val failure = IllegalStateException(message)
        scope.failed<Unit>(failure)
        return ExecuteResult.Failure(scope.firstFailure ?: failure)
    }

    private fun MigrationRunOutcome.toExecuteResult(): ExecuteResult<Boolean> = when (this) {
        is MigrationRunOutcome.PreflightFailure -> {
            if (failure.exception is Error) throw failure.exception
            failure
        }
        is MigrationRunOutcome.Attempt -> when (val attemptResult = result) {
            is MigrationExecutionResult.Success -> ExecuteResult.Success(true)
            is MigrationExecutionResult.Failure -> ExecuteResult.Failure(attemptResult.cause)
            is MigrationExecutionResult.Fatal -> throw attemptResult.error
        }
    }

    private fun failureResult(scope: OperationExecutionScope): ExecuteResult.Failure =
        ExecuteResult.Failure(scope.firstFailure ?: IllegalStateException("Migration Version operation failed"))

    private fun <T> ExecuteResult<T>.valueOrNull(scope: OperationExecutionScope): T? = when (this) {
        is ExecuteResult.Failure -> {
            scope.recordResult(this)
            null
        }
        is ExecuteResult.Success -> result
    }

    private data class VersionTagRow(val id: Int)
    private data class VersionRow(val id: Int, val tagId: Int)

    private companion object {
        // SystemMigrationVersionModel is internal to migration:core, so its status code cannot
        // be referenced from this module without changing Core visibility.
        const val STATUS_ROLLBACK_STARTED = MigrationVersionState.STATUS_ROLLBACK_STARTED
        const val STATUS_MIGRATE_COMPLETED = MigrationVersionState.STATUS_MIGRATE_COMPLETED
    }
}
