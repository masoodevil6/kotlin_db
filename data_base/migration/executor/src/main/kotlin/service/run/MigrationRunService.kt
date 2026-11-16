package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationDeleteQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationInsertQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagDeleteQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagInsertQuery
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.service.result.MigrationExecutionResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.QueryRow
import gog.my_project.data_base_schema.Schema
import java.lang.reflect.InvocationTargetException
import kotlin.jvm.java

/** Separates failures before a migration attempt from the outcome of an actual attempt. */
internal sealed interface MigrationRunOutcome {
    data class PreflightFailure(val failure: ExecuteResult.Failure) : MigrationRunOutcome

    data class Attempt(val result: MigrationExecutionResult) : MigrationRunOutcome
}

/** A registered Migration and the single runtime instance prepared for its execution. */
internal class PreparedMigration internal constructor(
    val registration: RegisteredMigration,
    val instance: Migration,
)

/** Executes one registered Migration within a caller-owned Operation transaction scope. */
internal class MigrationRunService {
    /** Runs exactly one Migration's up operation and records it under a persisted Version. */
    fun runUp(
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
    ): ExecuteResult<Boolean> = runUpInternal(null, versionId, scope, migration).toExecuteResult()

    /** Internal path used by the Version service to retain the Version tag in the attempt result. */
    internal fun runUpAttempt(
        versionTag: MigrationVersionTag,
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
    ): MigrationRunOutcome = runUpInternal(versionTag, versionId, scope, migration)

    /** Runs UP with an instance prepared before the Version marker was recorded. */
    internal fun runUpAttempt(
        versionTag: MigrationVersionTag,
        versionId: Int,
        scope: OperationExecutionScope,
        preparedMigration: PreparedMigration,
    ): MigrationRunOutcome = runUpInternal(
        versionTag,
        versionId,
        scope,
        preparedMigration.registration,
        preparedMigration,
    )

    private fun runUpInternal(
        versionTag: MigrationVersionTag?,
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
        preparedMigration: PreparedMigration? = null,
    ): MigrationRunOutcome {
        var attemptStarted = false
        var stage = MigrationExecutionResult.FailureStage.EXECUTION
        return try {
            scope.rejectOperation()?.let {
                if (it is Error) {
                    scope.recordFatal(it)
                    throw it
                }
                return MigrationRunOutcome.PreflightFailure(ExecuteResult.Failure(it))
            }
            requirePositiveVersionId(versionId, scope)?.let {
                return preflightFailure(scope, it.exception)
            }

            // Refuse an already applied item before invoking user migration code.
            val existingTag = readMigrationTagsByName(scope, migration.id.value).valueOrReturn(scope)
                ?: return preflightFailure(
                    scope,
                    failureAfterLookup(scope, "Could not read Migration Tag '${migration.id.value}'").exception,
                )
            if (existingTag.size > 1) {
                return preflightFailure(
                    scope,
                    failure(scope, "Duplicate Migration tags exist for '${migration.id.value}'").exception,
                )
            }
            existingTag.singleOrNull()?.let { tag ->
                val applied = readAppliedByTag(scope, tag.id).valueOrReturn(scope)
                    ?: return preflightFailure(
                        scope,
                        failureAfterLookup(scope, "Could not read Migration history for '${migration.id.value}'").exception,
                    )
                if (applied.isNotEmpty()) {
                    return preflightFailure(
                        scope,
                        failure(
                            scope,
                            "Migration '${migration.id.value}' is already applied under persisted Version " +
                                applied.joinToString { it.versionId.toString() },
                        ).exception,
                    )
                }
            }

            val instance = if (preparedMigration != null) {
                preparedMigration.instance
            } else {
                val prepared = prepareMigration(scope, migration).valueOrReturn(scope)
                    ?: return preflightFailure(
                        scope,
                        failureAfterLookup(scope, "Could not instantiate Migration '${migration.id.value}'").exception,
                    )
                prepared.instance
            }

            attemptStarted = true
            val upResult = Schema.execute(instance.up().operation, scope)
            when (upResult) {
                is ExecuteResult.Failure -> return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    MigrationExecutionResult.FailureStage.EXECUTION,
                    upResult.exception,
                )
                is ExecuteResult.Success -> if (upResult.result != true) {
                    return attemptFailure(
                        scope,
                        MigrationExecutionResult.Direction.UP,
                        versionTag,
                        versionId,
                        migration.id,
                        MigrationExecutionResult.FailureStage.EXECUTION,
                        IllegalStateException("Migration '${migration.id.value}' up() did not complete successfully"),
                    )
                }
            }

            stage = MigrationExecutionResult.FailureStage.HISTORY_RECORD

            // Resolve/create the item Tag only after the schema operation succeeds.
            val tagAfterUp = readMigrationTagsByName(scope, migration.id.value).valueOrReturn(scope)
                ?: return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failureAfterLookup(scope, "Could not read Migration Tag '${migration.id.value}'").exception,
                )
            if (tagAfterUp.size > 1) {
                return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failure(scope, "Duplicate Migration tags exist for '${migration.id.value}'").exception,
                )
            }
            val tagId = tagAfterUp.singleOrNull()?.id ?: insertMigrationTag(scope, migration.id.value)
                .valueOrReturn(scope)
                ?: return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failureAfterLookup(scope, "Could not create Migration Tag '${migration.id.value}'").exception,
                )

            // Recheck before writing history so a visible concurrent/conflicting record is never ignored.
            val conflictingHistory = readAppliedByTag(scope, tagId).valueOrReturn(scope)
                ?: return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failureAfterLookup(scope, "Could not verify Migration history for '${migration.id.value}'").exception,
                )
            if (conflictingHistory.isNotEmpty()) {
                return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failure(
                        scope,
                        "Migration '${migration.id.value}' acquired an applied history record before it could be recorded",
                    ).exception,
                )
            }

            val inserted = insertApplied(scope, tagId, versionId)
            if (inserted is ExecuteResult.Failure) {
                return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.UP,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    inserted.exception,
                )
            }
            MigrationRunOutcome.Attempt(
                MigrationExecutionResult.Success(
                    direction = MigrationExecutionResult.Direction.UP,
                    versionTag = versionTag,
                    versionRecordId = versionId,
                    migrationId = migration.id,
                ),
            )
        } catch (error: Error) {
            if (!attemptStarted) {
                scope.recordFatal(error)
                throw error
            }
            attemptFatal(
                scope,
                MigrationExecutionResult.Direction.UP,
                versionTag,
                versionId,
                migration.id,
                stage,
                error,
            )
        } catch (failure: Throwable) {
            if (!attemptStarted) return preflightFailure(scope, failure)
            attemptFailure(
                scope,
                MigrationExecutionResult.Direction.UP,
                versionTag,
                versionId,
                migration.id,
                stage,
                failure,
            )
        }
    }

    /** Runs exactly one Migration's down operation and removes only its matching Version record. */
    fun runDown(
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
    ): ExecuteResult<Boolean> = runDownInternal(null, versionId, scope, migration).toExecuteResult()

    /** Internal path used by the Version service to retain the Version tag in the attempt result. */
    internal fun runDownAttempt(
        versionTag: MigrationVersionTag,
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
    ): MigrationRunOutcome = runDownInternal(versionTag, versionId, scope, migration)

    /** Runs DOWN with an instance prepared before the Version marker was recorded. */
    internal fun runDownAttempt(
        versionTag: MigrationVersionTag,
        versionId: Int,
        scope: OperationExecutionScope,
        preparedMigration: PreparedMigration,
    ): MigrationRunOutcome = runDownInternal(
        versionTag,
        versionId,
        scope,
        preparedMigration.registration,
        preparedMigration,
    )

    private fun runDownInternal(
        versionTag: MigrationVersionTag?,
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
        preparedMigration: PreparedMigration? = null,
    ): MigrationRunOutcome {
        var attemptStarted = false
        var stage = MigrationExecutionResult.FailureStage.EXECUTION
        return try {
            scope.rejectOperation()?.let {
                if (it is Error) {
                    scope.recordFatal(it)
                    throw it
                }
                return MigrationRunOutcome.PreflightFailure(ExecuteResult.Failure(it))
            }
            requirePositiveVersionId(versionId, scope)?.let {
                return preflightFailure(scope, it.exception)
            }

            val namedTags = readTagsByName(scope, migration.id.value).valueOrReturn(scope)
                ?: return preflightFailure(
                    scope,
                    failureAfterLookup(scope, "Could not read Tag '${migration.id.value}'").exception,
                )
            if (namedTags.isEmpty()) {
                return preflightFailure(
                    scope,
                    failure(scope, "No applied history exists for Migration '${migration.id.value}'").exception,
                )
            }
            val migrationTags = namedTags.filter { it.tagType == MigrationTagType.ITEM.code }
            if (migrationTags.isEmpty()) {
                return preflightFailure(
                    scope,
                    failure(
                        scope,
                        "Tag '${migration.id.value}' exists but is not a Migration item Tag",
                    ).exception,
                )
            }
            if (migrationTags.size != 1) {
                return preflightFailure(
                    scope,
                    failure(scope, "Duplicate Migration item Tags exist for '${migration.id.value}'").exception,
                )
            }
            val tag = migrationTags.single()

            val applied = readAppliedByTag(scope, tag.id).valueOrReturn(scope)
                ?: return preflightFailure(
                    scope,
                    failureAfterLookup(scope, "Could not read Migration history for '${migration.id.value}'").exception,
                )
            val matchingHistory = applied.filter { it.versionId == versionId }
            if (matchingHistory.isEmpty()) {
                if (applied.isNotEmpty()) {
                    return preflightFailure(
                        scope,
                        failure(
                            scope,
                            "Migration '${migration.id.value}' is applied under persisted Version " +
                                applied.joinToString { it.versionId.toString() } + ", not $versionId",
                        ).exception,
                    )
                }
                return preflightFailure(
                    scope,
                    failure(scope, "No applied history exists for Migration '${migration.id.value}'").exception,
                )
            }
            if (matchingHistory.size != 1) {
                return preflightFailure(
                    scope,
                    failure(
                        scope,
                        "Migration '${migration.id.value}' has duplicate history under Version $versionId",
                    ).exception,
                )
            }

            val instance = if (preparedMigration != null) {
                preparedMigration.instance
            } else {
                val prepared = prepareMigration(scope, migration).valueOrReturn(scope)
                    ?: return preflightFailure(
                        scope,
                        failureAfterLookup(scope, "Could not instantiate Migration '${migration.id.value}'").exception,
                    )
                prepared.instance
            }

            attemptStarted = true
            val downResult = Schema.execute(instance.down().operation, scope)
            when (downResult) {
                is ExecuteResult.Failure -> return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.DOWN,
                    versionTag,
                    versionId,
                    migration.id,
                    MigrationExecutionResult.FailureStage.EXECUTION,
                    downResult.exception,
                )
                is ExecuteResult.Success -> if (downResult.result != true) {
                    return attemptFailure(
                        scope,
                        MigrationExecutionResult.Direction.DOWN,
                        versionTag,
                        versionId,
                        migration.id,
                        MigrationExecutionResult.FailureStage.EXECUTION,
                        IllegalStateException("Migration '${migration.id.value}' down() did not complete successfully"),
                    )
                }
            }

            stage = MigrationExecutionResult.FailureStage.HISTORY_RECORD

            val historyDelete = deleteApplied(scope, tag.id, versionId)
            if (historyDelete is ExecuteResult.Failure) {
                return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.DOWN,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    historyDelete.exception,
                )
            }
            val deletedHistoryCount = (historyDelete as ExecuteResult.Success).result
            if (deletedHistoryCount != 1) {
                return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.DOWN,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failure(
                        scope,
                        "Expected to delete one applied history record for '${migration.id.value}', " +
                            "but deleted $deletedHistoryCount",
                    ).exception,
                )
            }

            val remaining = readAppliedByTag(scope, tag.id).valueOrReturn(scope)
                ?: return attemptFailure(
                    scope,
                    MigrationExecutionResult.Direction.DOWN,
                    versionTag,
                    versionId,
                    migration.id,
                    stage,
                    failureAfterLookup(scope, "Could not verify remaining history for '${migration.id.value}'").exception,
                )
            if (remaining.isEmpty()) {
                val tagDelete = deleteMigrationTag(scope, tag.id)
                if (tagDelete is ExecuteResult.Failure) {
                    return attemptFailure(
                        scope,
                        MigrationExecutionResult.Direction.DOWN,
                        versionTag,
                        versionId,
                        migration.id,
                        stage,
                        tagDelete.exception,
                    )
                }
                val deletedTagCount = (tagDelete as ExecuteResult.Success).result
                if (deletedTagCount != 1) {
                    return attemptFailure(
                        scope,
                        MigrationExecutionResult.Direction.DOWN,
                        versionTag,
                        versionId,
                        migration.id,
                        stage,
                        failure(
                            scope,
                            "Expected to delete one unreferenced Migration Tag '${migration.id.value}', " +
                                "but deleted $deletedTagCount",
                        ).exception,
                    )
                }
            }

            MigrationRunOutcome.Attempt(
                MigrationExecutionResult.Success(
                    direction = MigrationExecutionResult.Direction.DOWN,
                    versionTag = versionTag,
                    versionRecordId = versionId,
                    migrationId = migration.id,
                ),
            )
        } catch (error: Error) {
            if (!attemptStarted) {
                scope.recordFatal(error)
                throw error
            }
            attemptFatal(
                scope,
                MigrationExecutionResult.Direction.DOWN,
                versionTag,
                versionId,
                migration.id,
                stage,
                error,
            )
        } catch (failure: Throwable) {
            if (!attemptStarted) return preflightFailure(scope, failure)
            attemptFailure(
                scope,
                MigrationExecutionResult.Direction.DOWN,
                versionTag,
                versionId,
                migration.id,
                stage,
                failure,
            )
        }
    }

    private fun requirePositiveVersionId(
        versionId: Int,
        scope: OperationExecutionScope,
    ): ExecuteResult.Failure? =
        if (versionId > 0) null else failure(scope, "Persisted Migration version id must be positive")

    /** Constructs and validates one runtime Migration instance without running its UP or DOWN callback. */
    internal fun prepareMigration(
        scope: OperationExecutionScope,
        registration: RegisteredMigration,
    ): ExecuteResult<PreparedMigration> {
        return try {
            val migration = registration.migrationClass.java.getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
            if (migration.identity != registration.id) {
                return scope.failed(
                    IllegalArgumentException(
                        "Registered Migration identity '${registration.id.value}' does not match " +
                            "${registration.migrationClass.qualifiedName}'s identity '${migration.identity.value}'",
                    ),
                )
            }
            ExecuteResult.Success(PreparedMigration(registration, migration))
        } catch (failure: Throwable) {
            val cause = (failure as? InvocationTargetException)?.targetException ?: failure
            if (cause is Error) {
                scope.recordFatal(cause)
                throw cause
            }
            val contextual = if (cause is NoSuchMethodException) {
                IllegalArgumentException(
                    "Migration class '${registration.migrationClass.qualifiedName}' must have a no-argument constructor",
                    cause,
                )
            } else {
                cause
            }
            scope.failed(contextual)
        }
    }

    private fun readMigrationTagsByName(
        scope: OperationExecutionScope,
        tagName: String,
    ): ExecuteResult<List<TagRow>> = readTags(
        scope,
        SystemMigrationTagQuery.RelationFilters(tagName = tagName, tagType = MigrationTagType.ITEM.code),
    )

    private fun readTagsByName(
        scope: OperationExecutionScope,
        tagName: String,
    ): ExecuteResult<List<TagRow>> = readTags(
        scope,
        SystemMigrationTagQuery.RelationFilters(tagName = tagName),
    )

    private fun readTags(
        scope: OperationExecutionScope,
        filters: SystemMigrationTagQuery.RelationFilters,
    ): ExecuteResult<List<TagRow>> {
        val cte = SystemMigrationTagQuery().createCte(filters)
        return read(scope, cte.definition.ast) { row ->
            TagRow(
                id = row.requiredInt("id", "Tag query returned a null id"),
                tagType = row.requiredInt("tagType", "Tag query returned a null tagType"),
            )
        }
    }

    private fun readAppliedByTag(
        scope: OperationExecutionScope,
        tagId: Int,
    ): ExecuteResult<List<AppliedRow>> {
        val cte = SystemMigrationQuery().createCte(SystemMigrationQuery.RelationFilters(tagId = tagId))
        return read(scope, cte.definition.ast) { row ->
            AppliedRow(
                id = row.requiredInt("id", "History query returned a null id"),
                versionId = row.requiredInt("versionId", "History query returned a null versionId"),
            )
        }
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
                    requireNotNull(result.result) { "System Migration query returned no rows" }.map(mapper),
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

    private fun insertMigrationTag(
        scope: OperationExecutionScope,
        tagName: String,
    ): ExecuteResult<Int> {
        val query = SystemMigrationTagInsertQuery(tagName, MigrationTagType.ITEM.code).query()
        val result = executeInsert(scope, query)
        return when (result) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> {
                val id = result.result ?: return failure(scope, "Migration Tag insert returned no generated id")
                if (id !in 1..Int.MAX_VALUE.toLong()) {
                    return failure(scope, "Generated Migration Tag id $id is outside the supported Int range")
                }
                ExecuteResult.Success(id.toInt())
            }
        }
    }

    private fun insertApplied(
        scope: OperationExecutionScope,
        tagId: Int,
        versionId: Int,
    ): ExecuteResult<Boolean> {
        val query = SystemMigrationInsertQuery(tagId, versionId).query()
        return when (val result = executeInsert(scope, query)) {
            is ExecuteResult.Failure -> result
            is ExecuteResult.Success -> if (result.result != null) ExecuteResult.Success(true)
            else failure(scope, "Migration history insert returned no generated id")
        }
    }

    private fun deleteApplied(
        scope: OperationExecutionScope,
        tagId: Int,
        versionId: Int,
    ): ExecuteResult<Int> = executeDelete(scope, SystemMigrationDeleteQuery(tagId, versionId).query())

    private fun deleteMigrationTag(
        scope: OperationExecutionScope,
        tagId: Int,
    ): ExecuteResult<Int> = executeDelete(scope, SystemMigrationTagDeleteQuery(tagId).query())

    private fun executeInsert(
        scope: OperationExecutionScope,
        query: IQueryRenderInsertApi,
    ): ExecuteResult<Long> = QueryBuilderExecutor.scopeBound(scope)
        .queryBuilder(query)
        .execute()

    private fun executeDelete(
        scope: OperationExecutionScope,
        query: IQueryRenderDeleteApi,
    ): ExecuteResult<Int> = QueryBuilderExecutor.scopeBound(scope)
        .queryBuilder(query)
        .execute()

    private fun preflightFailure(
        scope: OperationExecutionScope,
        failure: Throwable,
    ): MigrationRunOutcome.PreflightFailure {
        if (failure is Error) {
            scope.recordFatal(failure)
            throw failure
        }
        val primary = scope.recordFailure(failure)
        if (primary is Error) throw primary
        return MigrationRunOutcome.PreflightFailure(ExecuteResult.Failure(primary))
    }

    private fun attemptFailure(
        scope: OperationExecutionScope,
        direction: MigrationExecutionResult.Direction,
        versionTag: MigrationVersionTag?,
        versionId: Int,
        migrationId: MigrationIdentity,
        stage: MigrationExecutionResult.FailureStage,
        failure: Throwable,
    ): MigrationRunOutcome.Attempt {
        if (failure is Error) return attemptFatal(scope, direction, versionTag, versionId, migrationId, stage, failure)
        val primary = scope.recordFailure(failure)
        if (primary is Error) return attemptFatal(scope, direction, versionTag, versionId, migrationId, stage, primary)
        return MigrationRunOutcome.Attempt(
            MigrationExecutionResult.Failure(
                direction = direction,
                versionTag = versionTag,
                versionRecordId = versionId,
                migrationId = migrationId,
                stage = stage,
                cause = primary,
            ),
        )
    }

    private fun attemptFatal(
        scope: OperationExecutionScope,
        direction: MigrationExecutionResult.Direction,
        versionTag: MigrationVersionTag?,
        versionId: Int,
        migrationId: MigrationIdentity,
        stage: MigrationExecutionResult.FailureStage,
        error: Error,
    ): MigrationRunOutcome.Attempt {
        scope.recordFatal(error)
        return MigrationRunOutcome.Attempt(
            MigrationExecutionResult.Fatal(
                direction = direction,
                versionTag = versionTag,
                versionRecordId = versionId,
                migrationId = migrationId,
                stage = stage,
                error = error,
            ),
        )
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

    private fun failure(scope: OperationExecutionScope, message: String): ExecuteResult.Failure =
        ExecuteResult.Failure(scope.recordFailure(IllegalStateException(message)))

    private fun failureAfterLookup(
        scope: OperationExecutionScope,
        message: String,
    ): ExecuteResult.Failure = scope.firstFailure?.let { ExecuteResult.Failure(it) } ?: failure(scope, message)

    private fun <T> ExecuteResult<T>.valueOrReturn(scope: OperationExecutionScope): T? = when (this) {
        is ExecuteResult.Failure -> {
            scope.recordResult(this)
            null
        }
        is ExecuteResult.Success -> result
    }

    private data class TagRow(val id: Int, val tagType: Int)
    private data class AppliedRow(val id: Int, val versionId: Int)
}
