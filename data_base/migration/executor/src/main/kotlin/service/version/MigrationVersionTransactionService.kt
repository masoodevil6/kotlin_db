package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base_manager.execute.manager.QueryExecute
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationVersionQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationTagInsertQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationVersionDeleteQuery
import gog.my_project.data_base_migration.core.system_migration.query.SystemMigrationVersionInsertQuery
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.service.run.MigrationRunService
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
import model.version_history.MigrationVersionHistorySnapshot
import java.sql.Connection
import model.prepared.PreparedMigrationVersion
import service.version.MigrationVersionPreparationService

/** Runs all Migrations belonging to one Version inside a child transaction on the caller's Connection scope. */
class MigrationVersionTransactionService(
    private val configuration: MigrationConfiguration,
) {
    private val preparationService = MigrationVersionPreparationService()
    private val migrationRunService = MigrationRunService()
    private val queryExecutor = QueryExecute()
    private val historyReader = MigrationVersionHistoryReader()
    private val historyValidator = MigrationVersionHistoryValidator(preparationService)

    /** Applies one registered Version in a single child transaction on the supplied Connection scope. */
    fun transactionUp(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): ExecuteResult<Boolean> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        val prepared = prepare(scope, versionTag) ?: return failureResult(scope)

        return scope.transaction { transactionVersionScope ->
            safely(transactionVersionScope) {
                val tagId = resolveVersionTagForUp(transactionVersionScope, versionTag)
                    ?: return@safely failureResult(transactionVersionScope)

                val versionId = insertVersion(transactionVersionScope, tagId)
                    ?: return@safely failureResult(transactionVersionScope)

                for (migration in prepared.migrations) {
                    val result = migrationRunService.runUp(versionId, transactionVersionScope, migration)
                    if (result is ExecuteResult.Failure) return@safely result
                    if ((result as ExecuteResult.Success).result != true) {
                        return@safely fail(
                            transactionVersionScope,
                            "Migration '${migration.id.value}' did not complete successfully",
                        )
                    }
                }

                ExecuteResult.Success(true)
            }
        }
    }

    /** Rolls back one applied Version in a single child transaction, preserving reverse order. */
    fun transactionDown(
        scope: OperationExecutionScope,
        versionTag: MigrationVersionTag,
    ): ExecuteResult<Boolean> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        val prepared = prepare(scope, versionTag) ?: return failureResult(scope)

        return scope.transaction { transactionVersionScope ->
            safely(transactionVersionScope) {
                val snapshot = when (val result = historyReader.read(transactionVersionScope)) {
                    is ExecuteResult.Failure -> {
                        transactionVersionScope.recordResult(result)
                        return@safely failureResult(transactionVersionScope)
                    }
                    is ExecuteResult.Success -> requireNotNull(result.result)
                }
                val matchingTags = snapshot.tags.filter {
                    it.name == versionTag.validatedValue && it.type == MigrationTagType.VERSION.code
                }
                if (matchingTags.size != 1) {
                    return@safely fail(
                        transactionVersionScope,
                        if (matchingTags.isEmpty()) {
                            "Version Tag '${versionTag.validatedValue}' is not registered"
                        } else {
                            "Duplicate Version Tags exist for '${versionTag.validatedValue}'"
                        },
                    )
                }
                val tagId = matchingTags.single().id
                val persistedVersions = snapshot.versions.filter { it.tagId == tagId }
                if (persistedVersions.isEmpty()) {
                    return@safely fail(
                        transactionVersionScope,
                        "Migration Version '${versionTag.validatedValue}' has not been applied",
                    )
                }
                if (persistedVersions.size != 1) {
                    return@safely fail(
                        transactionVersionScope,
                        "Duplicate persisted Migration Versions exist for '${versionTag.validatedValue}'",
                    )
                }
                val persistedVersion = persistedVersions.single()
                if (persistedVersion.tagId != tagId) {
                    return@safely fail(
                        transactionVersionScope,
                        "Persisted Migration Version '${versionTag.validatedValue}' resolved to an unexpected Tag",
                    )
                }
                val versionId = persistedVersion.id

                validateHistory(transactionVersionScope, snapshot, versionId, prepared.migrations)
                    ?.let { return@safely it }

                for (migration in prepared.migrations.asReversed()) {
                    val result = migrationRunService.runDown(versionId, transactionVersionScope, migration)
                    if (result is ExecuteResult.Failure) return@safely result
                    if ((result as ExecuteResult.Success).result != true) {
                        return@safely fail(
                            transactionVersionScope,
                            "Migration '${migration.id.value}' did not roll back successfully",
                        )
                    }
                }

                val deleted = executeDelete(
                    transactionVersionScope,
                    SystemMigrationVersionDeleteQuery(versionId).query(),
                ) ?: return@safely failureResult(transactionVersionScope)
                if (deleted != 1) {
                    return@safely fail(
                        transactionVersionScope,
                        "Expected to delete one Migration Version '${versionTag.validatedValue}', but deleted $deleted",
                    )
                }

                ExecuteResult.Success(true)
            }
        }
    }

    private fun <T> safely(
        scope: OperationExecutionScope,
        block: () -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        return try {
            block().also(scope::recordResult)
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed(failure)
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
    ).definition.ast) { rows ->
        buildList {
            while (rows.next()) {
                add(
                    VersionTagRow(
                        id = requireNotNull(rows.getInt("id")) { "Version Tag query returned a null id" },
                    ),
                )
            }
        }
    }.valueOrNull(scope)

    private fun readVersions(scope: OperationExecutionScope, tagId: Int): List<VersionRow>? =
        read(scope, SystemMigrationVersionQuery().createCte(
            SystemMigrationVersionQuery.RelationFilters(tagId = tagId),
        ).definition.ast) { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        VersionRow(
                            id = requireNotNull(rows.getInt("id")) { "Version query returned a null id" },
                            tagId = requireNotNull(rows.getInt("tagId")) { "Version query returned a null tagId" },
                        ),
                    )
                }
            }
        }.valueOrNull(scope)

    private fun insertVersion(scope: OperationExecutionScope, tagId: Int): Int? {
        val generatedId = executeInsert(scope, SystemMigrationVersionInsertQuery(tagId).query()) ?: return null
        return generatedId.toPositiveIntId(scope, "Generated Migration Version id")
    }

    private fun <T> read(
        scope: OperationExecutionScope,
        ast: IQueryRenderSelectAst,
        mapper: (OperationQueryRows) -> T,
    ): ExecuteResult<T> {
        val dialect = DialectSelector().select(scope.databasePlatformConfig.dialect)
        return when (val rendered = QueryParameterRendering.render(ast, dialect)) {
            is IdentityAwareQueryRenderResult -> {
                val bindings = distinctBindings(rendered.parameterOccurrences, scope) ?: return failureResult(scope)
                scope.executeRead(
                    rendered.displaySql,
                    bindings.map { binding -> OperationParameter(binding.name, binding.value, binding.sqlType) },
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

    private fun distinctBindings(
        occurrences: List<IQueryParameterBinding>,
        scope: OperationExecutionScope,
    ): List<IQueryParameterBinding>? {
        val bindings = linkedMapOf<String, IQueryParameterBinding>()
        return try {
            occurrences.forEach { binding ->
                val previous = bindings.putIfAbsent(binding.name, binding)
                require(previous == null ||
                    (previous.value == binding.value && previous.sqlType == binding.sqlType)
                ) { "Conflicting values rendered for System Migration parameter '${binding.name}'" }
            }
            bindings.values.toList()
        } catch (failure: Exception) {
            scope.failed<Any?>(failure)
            null
        }
    }

    private fun executeInsert(
        scope: OperationExecutionScope,
        query: IQueryRenderInsertApi,
    ): Long? = executeDml(scope, query.ast) { connection, boundQuery, callback ->
        queryExecutor.executeInsert(connection, boundQuery, callback)
    }.valueOrNull(scope)

    private fun executeDelete(
        scope: OperationExecutionScope,
        query: IQueryRenderDeleteApi,
    ): Int? = executeDml(scope, query.ast) { connection, boundQuery, callback ->
        queryExecutor.executeDelete(connection, boundQuery, callback)
    }.valueOrNull(scope)

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

        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        var completed: ExecuteResult<T>? = null
        @Suppress("DEPRECATION")
        execute(scope.connection, boundQuery) { result -> completed = result }
        val result = completed ?: scope.failed(
            IllegalStateException("System Migration DML execution completed without a result"),
        )
        scope.recordResult(result)
        return when (result) {
            is ExecuteResult.Failure -> ExecuteResult.Failure(scope.firstFailure ?: result.exception)
            is ExecuteResult.Success -> result
        }
    }

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
}
