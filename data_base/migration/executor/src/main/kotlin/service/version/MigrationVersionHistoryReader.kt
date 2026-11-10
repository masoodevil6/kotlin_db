package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationTagQuery
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationVersionQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.QueryRow
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

    private fun readTags(scope: OperationExecutionScope): ExecuteResult<List<MigrationHistoryTag>> {
        val cte = SystemMigrationTagQuery().createCte(SystemMigrationTagQuery.RelationFilters())
        return read(scope, cte.definition.ast) { row ->
            MigrationHistoryTag(
                id = row.requiredInt("id", "Tag query returned a null id"),
                name = row.requiredString("tagName", "Migration Tag query returned a null name"),
                type = row.requiredInt("tagType", "Migration Tag query returned a null type"),
            )
        }
    }

    private fun readVersions(scope: OperationExecutionScope): ExecuteResult<List<MigrationHistoryVersion>> {
        val cte = SystemMigrationVersionQuery().createCte(SystemMigrationVersionQuery.RelationFilters())
        return read(scope, cte.definition.ast) { row ->
            MigrationHistoryVersion(
                id = row.requiredInt("id", "Migration Version query returned a null id"),
                tagId = row.requiredInt("tagId", "Migration Version query returned a null tagId"),
            )
        }
    }

    private fun readMigrations(scope: OperationExecutionScope): ExecuteResult<List<MigrationHistoryEntry>> {
        val cte = SystemMigrationQuery().createCte(SystemMigrationQuery.RelationFilters())
        return read(scope, cte.definition.ast) { row ->
            MigrationHistoryEntry(
                id = row.requiredInt("id", "Applied Migration query returned a null id"),
                tagId = row.requiredInt("tagId", "Applied Migration query returned a null tagId"),
                versionId = row.requiredInt("versionId", "Applied Migration query returned a null versionId"),
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
                    requireNotNull(result.result) { "System Migration history query returned no rows" }
                        .map(mapper),
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

    private fun QueryRow.requiredString(alias: String, failureMessage: String): String =
        requireNotNull(this[alias] as? String) { failureMessage }
}
