package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationVersionQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationParameter
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base_query.renderer.manager.DialectSelector
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot

/**
 * Reads one full migration-history snapshot through the caller-owned Operation scope.
 * The queries are sequential, so the coordinator must hold its Migration Lock before this read
 * and keep it held until all Versions planned from this snapshot have completed.
 */
internal class MigrationVersionHistoryReader {
    fun read(scope: OperationExecutionScope): ExecuteResult<MigrationVersionHistorySnapshot> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }

        val tags = readTags(scope)
        if (tags is ExecuteResult.Failure) return tags
        val versions = readVersions(scope)
        if (versions is ExecuteResult.Failure) return versions
        val migrations = readMigrations(scope)
        if (migrations is ExecuteResult.Failure) return migrations

        return ExecuteResult.Success(
            MigrationVersionHistorySnapshot(
                requireNotNull((tags as ExecuteResult.Success).result),
                requireNotNull((versions as ExecuteResult.Success).result),
                requireNotNull((migrations as ExecuteResult.Success).result),
            ),
        )
    }

    private fun readTags(scope: OperationExecutionScope): ExecuteResult<List<MigrationHistoryTag>> =
        read(scope, SystemMigrationTagQuery().createCte(SystemMigrationTagQuery.RelationFilters()).definition.ast) {
            rows ->
            buildList {
                while (rows.next()) {
                    add(
                        MigrationHistoryTag(
                            id = requireNotNull(rows.getInt("id")) { "Migration Tag query returned a null id" },
                            name = requireNotNull(rows.getString("tagName")) {
                                "Migration Tag query returned a null name"
                            },
                            type = requireNotNull(rows.getInt("tagType")) {
                                "Migration Tag query returned a null type"
                            },
                        ),
                    )
                }
            }
        }

    private fun readVersions(scope: OperationExecutionScope): ExecuteResult<List<MigrationHistoryVersion>> =
        read(
            scope,
            SystemMigrationVersionQuery().createCte(SystemMigrationVersionQuery.RelationFilters()).definition.ast,
        ) { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        MigrationHistoryVersion(
                            id = requireNotNull(rows.getInt("id")) { "Migration Version query returned a null id" },
                            tagId = requireNotNull(rows.getInt("tagId")) {
                                "Migration Version query returned a null tagId"
                            },
                        ),
                    )
                }
            }
        }

    private fun readMigrations(scope: OperationExecutionScope): ExecuteResult<List<MigrationHistoryEntry>> =
        read(scope, SystemMigrationQuery().createCte(SystemMigrationQuery.RelationFilters()).definition.ast) { rows ->
            buildList {
                while (rows.next()) {
                    add(
                        MigrationHistoryEntry(
                            id = requireNotNull(rows.getInt("id")) { "Applied Migration query returned a null id" },
                            tagId = requireNotNull(rows.getInt("tagId")) {
                                "Applied Migration query returned a null tagId"
                            },
                            versionId = requireNotNull(rows.getInt("versionId")) {
                                "Applied Migration query returned a null versionId"
                            },
                        ),
                    )
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
                val parameters = distinctBindings(rendered.parameterOccurrences, scope)
                    ?: return ExecuteResult.Failure(scope.firstFailure ?: IllegalStateException("Invalid bindings"))
                scope.executeRead(
                    rendered.displaySql,
                    parameters.map { OperationParameter(it.name, it.value, it.sqlType) },
                    mapper,
                )
            }
            is LegacyQueryRenderResult -> scope.executeRead(
                requireNotNull(rendered.sql) { "System Migration history query rendered no SQL" },
                emptyList(),
                mapper,
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
                require(previous == null || (previous.value == binding.value && previous.sqlType == binding.sqlType)) {
                    "Conflicting values rendered for System Migration parameter '${binding.name}'"
                }
            }
            bindings.values.toList()
        } catch (failure: Exception) {
            scope.failed<Any?>(failure)
            null
        }
    }
}
