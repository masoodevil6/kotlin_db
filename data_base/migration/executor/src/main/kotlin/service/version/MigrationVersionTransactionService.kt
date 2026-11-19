package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationVersionQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagInsertQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationVersionDeleteQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationVersionInsertQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationVersionUpdateQuery
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.service.run.PreparedMigration
import gog.my_project.data_base_migration.executor.service.run.MigrationRunService
import gog.my_project.data_base_migration.executor.service.run.MigrationRunOutcome
import gog.my_project.data_base_migration.executor.service.result.MigrationExecutionResult
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnostic
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureDiagnosticStage
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCapability
import gog.my_project.data_base_operation.migration.failure.spi.MigrationFailureStatusCommand
import gog.my_project.data_base_operation.migration.failure.spi.write
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
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

        val versionId = insertVersion(scope, tagId)
            ?: return@safely failureResult(scope)

        for ((index, migration) in prepared.migrations.withIndex()) {
            val runOutcome = preparedMigrations?.let { runtimeMigrations ->
                migrationRunService.runUpAttempt(versionTag, versionId, scope, runtimeMigrations[index])
            } ?: migrationRunService.runUpAttempt(versionTag, versionId, scope, migration)
            val result = when (runOutcome) {
                is MigrationRunOutcome.PreflightFailure -> runOutcome.toExecuteResult()
                is MigrationRunOutcome.Attempt -> when (val attempt = runOutcome.result) {
                    is MigrationExecutionResult.Failure -> {
                        recordUpAttemptFailure(
                            scope,
                            failureStatusCapability,
                            versionId,
                            attempt,
                        )
                        ExecuteResult.Failure(attempt.cause)
                    }
                    is MigrationExecutionResult.Fatal -> throw attempt.error
                    is MigrationExecutionResult.Success -> ExecuteResult.Success(true)
                }
            }
            if (result is ExecuteResult.Failure) return@safely result
            if ((result as ExecuteResult.Success).result != true) {
                return@safely fail(
                    scope,
                    "Migration '${migration.id.value}' did not complete successfully",
                )
            }
        }

        val updated = executeUpdate(
            scope,
            SystemMigrationVersionUpdateQuery(versionId, STATUS_MIGRATE_COMPLETED).query(),
        ) ?: run {
            recordLatchedFailureStatus(
                scope,
                failureStatusCapability,
                versionId,
                MigrationFailureStatusCommand.UpRecordingFailure(versionId),
                migrationId = null,
            )
            return@safely failureResult(scope)
        }
        if (updated != 1) {
            val failure = fail(
                scope,
                "Expected to update one Migration Version '${versionTag.validatedValue}' to COMPLETED, but updated $updated rows",
            )
            recordLatchedFailureStatus(
                scope,
                failureStatusCapability,
                versionId,
                MigrationFailureStatusCommand.UpRecordingFailure(versionId),
                migrationId = null,
            )
            return@safely failure
        }

        ExecuteResult.Success(true)
    }

    private fun recordUpAttemptFailure(
        scope: OperationExecutionScope,
        capability: MigrationFailureStatusCapability?,
        versionId: Int,
        failure: MigrationExecutionResult.Failure,
    ) {
        if (failure.direction != MigrationExecutionResult.Direction.UP ||
            failure.versionRecordId != versionId
        ) return

        val command = when (failure.stage) {
            MigrationExecutionResult.FailureStage.EXECUTION ->
                MigrationFailureStatusCommand.UpExecutionFailure(versionId)
            MigrationExecutionResult.FailureStage.HISTORY_RECORD ->
                MigrationFailureStatusCommand.UpRecordingFailure(versionId)
            MigrationExecutionResult.FailureStage.VERSION_RECORD -> return
        }
        recordLatchedFailureStatus(
            scope,
            capability,
            versionId,
            command,
            migrationId = failure.migrationId?.value,
            expectedFailure = failure.cause,
        )
    }

    private fun recordDownAttemptFailure(
        scope: OperationExecutionScope,
        capability: MigrationFailureStatusCapability?,
        versionId: Int,
        failure: MigrationExecutionResult.Failure,
    ) {
        if (failure.direction != MigrationExecutionResult.Direction.DOWN ||
            failure.versionRecordId != versionId
        ) return

        val command = when (failure.stage) {
            MigrationExecutionResult.FailureStage.EXECUTION ->
                MigrationFailureStatusCommand.DownExecutionFailure(versionId)
            MigrationExecutionResult.FailureStage.HISTORY_RECORD ->
                MigrationFailureStatusCommand.DownRecordingFailure(versionId)
            MigrationExecutionResult.FailureStage.VERSION_RECORD -> return
        }
        recordLatchedFailureStatus(
            scope,
            capability,
            versionId,
            command,
            migrationId = failure.migrationId?.value,
            expectedFailure = failure.cause,
        )
    }

    /** Writes only after the ordinary Operation failure has already become authoritative. */
    private fun recordLatchedFailureStatus(
        scope: OperationExecutionScope,
        capability: MigrationFailureStatusCapability?,
        versionId: Int,
        command: MigrationFailureStatusCommand,
        migrationId: String?,
        expectedFailure: Throwable? = null,
    ) {
        if (capability == null || versionId <= 0 || scope.fatalError != null) return
        val primaryFailure = scope.firstFailure ?: return
        if (primaryFailure is Error || (expectedFailure != null && primaryFailure !== expectedFailure)) return

        // The capability is one-shot. Its result is terminal: no retry, fallback, or recursive write.
        val failure = expectedFailure ?: primaryFailure
        val diagnostic = failureDiagnosticSafely(scope, command, failure, migrationId)
        if (diagnostic == null) {
            capability.write(command)
        } else {
            capability.write(command, diagnostic)
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

        val updated = executeUpdate(
            scope,
            SystemMigrationVersionUpdateQuery(versionId, STATUS_ROLLBACK_STARTED).query(),
        ) ?: return@safely failureResult(scope)
        if (updated != 1) {
            return@safely fail(
                scope,
                "Expected to update one Migration Version '${versionTag.validatedValue}' to ROLLBACK_STARTED, but updated $updated rows",
            )
        }

        for (index in prepared.migrations.indices.reversed()) {
            val migration = prepared.migrations[index]
            val runOutcome = preparedMigrations?.let { runtimeMigrations ->
                migrationRunService.runDownAttempt(versionTag, versionId, scope, runtimeMigrations[index])
            } ?: migrationRunService.runDownAttempt(versionTag, versionId, scope, migration)
            val result = when (runOutcome) {
                is MigrationRunOutcome.PreflightFailure -> runOutcome.toExecuteResult()
                is MigrationRunOutcome.Attempt -> when (val attempt = runOutcome.result) {
                    is MigrationExecutionResult.Failure -> {
                        recordDownAttemptFailure(
                            scope,
                            failureStatusCapability,
                            versionId,
                            attempt,
                        )
                        ExecuteResult.Failure(attempt.cause)
                    }
                    is MigrationExecutionResult.Fatal -> throw attempt.error
                    is MigrationExecutionResult.Success -> ExecuteResult.Success(true)
                }
            }
            if (result is ExecuteResult.Failure) return@safely result
            if ((result as ExecuteResult.Success).result != true) {
                return@safely fail(
                    scope,
                    "Migration '${migration.id.value}' did not roll back successfully",
                )
            }
        }

        val deleted = executeDelete(
            scope,
            SystemMigrationVersionDeleteQuery(versionId).query(),
        ) ?: run {
            recordLatchedFailureStatus(
                scope,
                failureStatusCapability,
                versionId,
                MigrationFailureStatusCommand.DownRecordingFailure(versionId),
                migrationId = null,
            )
            return@safely failureResult(scope)
        }
        if (deleted != 1) {
            val failure = fail(
                scope,
                "Expected to delete one Migration Version '${versionTag.validatedValue}', but deleted $deleted",
            )
            recordLatchedFailureStatus(
                scope,
                failureStatusCapability,
                versionId,
                MigrationFailureStatusCommand.DownRecordingFailure(versionId),
                migrationId = null,
            )
            return@safely failure
        }

        ExecuteResult.Success(true)
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

    private fun insertVersion(scope: OperationExecutionScope, tagId: Int): Int? {
        val generatedId = executeInsert(scope, SystemMigrationVersionInsertQuery(tagId).query()) ?: return null
        return generatedId.toPositiveIntId(scope, "Generated Migration Version id")
    }

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

    private fun executeDelete(
        scope: OperationExecutionScope,
        query: IQueryRenderDeleteApi,
    ): Int? = QueryBuilderExecutor.scopeBound(scope)
        .queryBuilder(query)
        .execute()
        .valueOrNull(scope)

    private fun executeUpdate(
        scope: OperationExecutionScope,
        query: IQueryRenderUpdateApi,
    ): Int? = QueryBuilderExecutor.scopeBound(scope)
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
        const val STATUS_ROLLBACK_STARTED = 4
        const val STATUS_MIGRATE_COMPLETED = 3
    }
}
