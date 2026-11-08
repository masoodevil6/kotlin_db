package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationDeleteQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationInsertQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagDeleteQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagInsertQuery
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_query.ast.interfaces.IQueryAst
import gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base_query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base_query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base_query.renderer.manager.DialectSelector
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import gog.my_project.data_base_schema.Schema
import java.lang.reflect.InvocationTargetException
import java.sql.Connection
import kotlin.jvm.java

/** Executes one registered Migration within a caller-owned Operation transaction scope. */
class MigrationRunService {
    private val queryExecutor = QueryExecute()

    /** Runs exactly one Migration's up operation and records it under a persisted Version. */
    fun runUp(
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
    ): ExecuteResult<Boolean> = safely(scope) {
        requirePositiveVersionId(versionId, scope)?.let { return@safely it }

        // Refuse an already applied item before invoking user migration code.
        val existingTag = readMigrationTagsByName(scope, migration.id.value).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not read Migration Tag '${migration.id.value}'")
        if (existingTag.size > 1) {
            return@safely failure(scope, "Duplicate Migration tags exist for '${migration.id.value}'")
        }
        existingTag.singleOrNull()?.let { tag ->
            val applied = readAppliedByTag(scope, tag.id).valueOrReturn(scope)
                ?: return@safely failureAfterLookup(scope, "Could not read Migration history for '${migration.id.value}'")
            if (applied.isNotEmpty()) {
                return@safely failure(
                    scope,
                    "Migration '${migration.id.value}' is already applied under persisted Version " +
                        applied.joinToString { it.versionId.toString() },
                )
            }
        }

        val instance = instantiate(scope, migration).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not instantiate Migration '${migration.id.value}'")
        val upResult = Schema.execute(instance.up().operation, scope)
        when (upResult) {
            is ExecuteResult.Failure -> return@safely upResult
            is ExecuteResult.Success -> if (upResult.result != true) {
                return@safely failure(scope, "Migration '${migration.id.value}' up() did not complete successfully")
            }
        }

        // Resolve/create the item Tag only after the schema operation succeeds.
        val tagAfterUp = readMigrationTagsByName(scope, migration.id.value).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not read Migration Tag '${migration.id.value}'")
        if (tagAfterUp.size > 1) {
            return@safely failure(scope, "Duplicate Migration tags exist for '${migration.id.value}'")
        }
        val tagId = tagAfterUp.singleOrNull()?.id ?: insertMigrationTag(scope, migration.id.value)
            .valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not create Migration Tag '${migration.id.value}'")

        // Recheck before writing history so a visible concurrent/conflicting record is never ignored.
        val conflictingHistory = readAppliedByTag(scope, tagId).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not verify Migration history for '${migration.id.value}'")
        if (conflictingHistory.isNotEmpty()) {
            return@safely failure(
                scope,
                "Migration '${migration.id.value}' acquired an applied history record before it could be recorded",
            )
        }

        val inserted = insertApplied(scope, tagId, versionId)
        if (inserted is ExecuteResult.Failure) return@safely inserted
        ExecuteResult.Success(true)
    }

    /** Runs exactly one Migration's down operation and removes only its matching Version record. */
    fun runDown(
        versionId: Int,
        scope: OperationExecutionScope,
        migration: RegisteredMigration,
    ): ExecuteResult<Boolean> = safely(scope) {
        requirePositiveVersionId(versionId, scope)?.let { return@safely it }

        val namedTags = readTagsByName(scope, migration.id.value).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not read Tag '${migration.id.value}'")
        if (namedTags.isEmpty()) {
            return@safely failure(scope, "No applied history exists for Migration '${migration.id.value}'")
        }
        val migrationTags = namedTags.filter { it.tagType == MigrationTagType.ITEM.code }
        if (migrationTags.isEmpty()) {
            return@safely failure(
                scope,
                "Tag '${migration.id.value}' exists but is not a Migration item Tag",
            )
        }
        if (migrationTags.size != 1) {
            return@safely failure(scope, "Duplicate Migration item Tags exist for '${migration.id.value}'")
        }
        val tag = migrationTags.single()

        val applied = readAppliedByTag(scope, tag.id).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not read Migration history for '${migration.id.value}'")
        val matchingHistory = applied.filter { it.versionId == versionId }
        if (matchingHistory.isEmpty()) {
            if (applied.isNotEmpty()) {
                return@safely failure(
                    scope,
                    "Migration '${migration.id.value}' is applied under persisted Version " +
                        applied.joinToString { it.versionId.toString() } + ", not $versionId",
                )
            }
            return@safely failure(scope, "No applied history exists for Migration '${migration.id.value}'")
        }
        if (matchingHistory.size != 1) {
            return@safely failure(
                scope,
                "Migration '${migration.id.value}' has duplicate history under Version $versionId",
            )
        }

        val instance = instantiate(scope, migration).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not instantiate Migration '${migration.id.value}'")
        val downResult = Schema.execute(instance.down().operation, scope)
        when (downResult) {
            is ExecuteResult.Failure -> return@safely downResult
            is ExecuteResult.Success -> if (downResult.result != true) {
                return@safely failure(scope, "Migration '${migration.id.value}' down() did not complete successfully")
            }
        }

        val historyDelete = deleteApplied(scope, tag.id, versionId)
        if (historyDelete is ExecuteResult.Failure) return@safely historyDelete
        val deletedHistoryCount = (historyDelete as ExecuteResult.Success).result
        if (deletedHistoryCount != 1) {
            return@safely failure(
                scope,
                "Expected to delete one applied history record for '${migration.id.value}', " +
                    "but deleted $deletedHistoryCount",
            )
        }

        val remaining = readAppliedByTag(scope, tag.id).valueOrReturn(scope)
            ?: return@safely failureAfterLookup(scope, "Could not verify remaining history for '${migration.id.value}'")
        if (remaining.isEmpty()) {
            val tagDelete = deleteMigrationTag(scope, tag.id)
            if (tagDelete is ExecuteResult.Failure) return@safely tagDelete
            val deletedTagCount = (tagDelete as ExecuteResult.Success).result
            if (deletedTagCount != 1) {
                return@safely failure(
                    scope,
                    "Expected to delete one unreferenced Migration Tag '${migration.id.value}', " +
                        "but deleted $deletedTagCount",
                )
            }
        }

        ExecuteResult.Success(true)
    }

    private fun requirePositiveVersionId(
        versionId: Int,
        scope: OperationExecutionScope,
    ): ExecuteResult.Failure? =
        if (versionId > 0) null else failure(scope, "Persisted Migration version id must be positive")

    private fun instantiate(
        scope: OperationExecutionScope,
        registration: RegisteredMigration,
    ): ExecuteResult<Migration> {
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
            ExecuteResult.Success(migration)
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
        return read(scope, cte.definition.ast) { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        TagRow(
                            id = requireNotNull(rows.getInt("id")) { "Tag query returned a null id" },
                            tagType = requireNotNull(rows.getInt("tagType")) { "Tag query returned a null tagType" },
                        ),
                    )
                }
            }
        }
    }

    private fun readAppliedByTag(
        scope: OperationExecutionScope,
        tagId: Int,
    ): ExecuteResult<List<AppliedRow>> {
        val cte = SystemMigrationQuery().createCte(SystemMigrationQuery.RelationFilters(tagId = tagId))
        return read(scope, cte.definition.ast) { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        AppliedRow(
                            id = requireNotNull(rows.getInt("id")) { "History query returned a null id" },
                            versionId = requireNotNull(rows.getInt("versionId")) {
                                "History query returned a null versionId"
                            },
                        ),
                    )
                }
            }
        }
    }

    private fun <T> read(
        scope: OperationExecutionScope,
        ast: IQueryRenderSelectAst,
        mapper: (OperationQueryRows) -> T,
    ): ExecuteResult<T> {
        val dialect = DialectSelector().select(scope.databasePlatformConfig.dialect)
        return when (val rendered = QueryParameterRendering.render(ast, dialect)) {
            is IdentityAwareQueryRenderResult -> {
                val bindings = linkedMapOf<String, IQueryParameterBinding>()
                rendered.parameterOccurrences.forEach { binding ->
                    val previous = bindings.putIfAbsent(binding.name, binding)
                    require(previous == null ||
                        (previous.value == binding.value && previous.sqlType == binding.sqlType)
                    ) { "Conflicting values rendered for System Migration parameter '${binding.name}'" }
                }
                scope.executeRead(
                    rendered.displaySql,
                    bindings.values.map { binding ->
                        OperationParameter(
                            binding.name,
                            binding.value,
                            binding.sqlType,
                        )
                    },
                    mapper,
                )
            }
            is LegacyQueryRenderResult -> scope.failed(
                IllegalStateException(
                    "System Migration lookup rendered without identity-aware parameter bindings: ${rendered.sql}",
                ),
            )
        }
    }

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
    ): ExecuteResult<Long> = executeDml(scope, query.ast, execute = { connection, boundQuery, callback ->
        queryExecutor.executeInsert(connection, boundQuery, callback)
    })

    private fun executeDelete(
        scope: OperationExecutionScope,
        query: IQueryRenderDeleteApi,
    ): ExecuteResult<Int> = executeDml(scope, query.ast, execute = { connection, boundQuery, callback ->
        queryExecutor.executeDelete(connection, boundQuery, callback)
    })

    private fun <T> executeDml(
        scope: OperationExecutionScope,
        ast: IQueryAst<*>,
        execute: (Connection, BoundQuery, (ExecuteResult<T>) -> Unit) -> Unit,
    ): ExecuteResult<T> {
        val dialect = DialectSelector().select(scope.databasePlatformConfig.dialect)
        val rendered = QueryParameterRendering.renderDml(ast, dialect)
        val boundQuery = when (rendered) {
            is IdentityAwareQueryRenderResult -> BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences)
            is LegacyQueryRenderResult -> return scope.failed(
                IllegalStateException("System Migration DML rendered without parameter bindings: ${rendered.sql}"),
            )
        }

        val rejection = scope.rejectOperation()
        if (rejection != null) return ExecuteResult.Failure(rejection)
        var completed: ExecuteResult<T>? = null
        @Suppress("DEPRECATION")
        // This service owns a final QueryExecute instance. Its supplied-connection BoundQuery
        // overloads invoke the callback inline, so the result is set before execute returns.
        execute(scope.connection, boundQuery) { result -> completed = result }
        val result = completed ?: ExecuteResult.Failure(
            IllegalStateException("System Migration DML execution completed without a result"),
        )
        scope.recordResult(result)
        return when (result) {
            is ExecuteResult.Failure -> ExecuteResult.Failure(scope.firstFailure ?: result.exception)
            is ExecuteResult.Success -> result
        }
    }

    private inline fun <T> safely(
        scope: OperationExecutionScope,
        block: () -> ExecuteResult<T>,
    ): ExecuteResult<T> = try {
        val rejection = scope.rejectOperation()
        if (rejection != null) ExecuteResult.Failure(rejection) else block()
    } catch (error: Error) {
        scope.recordFatal(error)
        throw error
    } catch (failure: Throwable) {
        scope.failed(failure)
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
