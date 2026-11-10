package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationStatusQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.executor.manager.QueryBuilderExecutor
import gog.my_project.data_base_query.executor.result.QueryRow
import model.migration_status.MigrationStatusRow
import model.migration_status.MigrationStatusSnapshot

/** Reads the combined System Migration status CTE through the caller-owned Operation scope. */
internal class MigrationStatusReader {
    fun read(scope: OperationExecutionScope): ExecuteResult<MigrationStatusSnapshot> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }

        return try {
            val declaration = SystemMigrationStatusQuery()
            val ast = declaration.createCte(SystemMigrationStatusQuery.RelationFilters()).definition.ast
            val result = QueryBuilderExecutor.scopeBound(scope)
                .queryBuilder(QueryRenderSelectBuilder(ast = ast))
                .execute()

            when (result) {
                is ExecuteResult.Failure -> result
                is ExecuteResult.Success -> ExecuteResult.Success(
                    MigrationStatusSnapshot(
                        requireNotNull(result.result) { "System Migration status query returned no rows" }
                            .map(::toStatusRow),
                    ),
                )
            }
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed(failure)
        }
    }

    private fun toStatusRow(row: QueryRow): MigrationStatusRow = MigrationStatusRow(
        tagId = row.requiredInt(TAG_ID, "Status query returned a null tagId"),
        tagName = row.requiredString(TAG_NAME, "Status query returned a null tagName"),
        tagType = row.requiredInt(TAG_TYPE, "Status query returned a null tagType"),
        tagVersionId = row.nullableInt(TAG_VERSION_ID),
        migrationHistoryId = row.nullableInt(MIGRATION_HISTORY_ID),
        appliedVersionReferenceId = row.nullableInt(APPLIED_VERSION_REFERENCE_ID),
        appliedVersionId = row.nullableInt(APPLIED_VERSION_ID),
        appliedVersionTagId = row.nullableInt(APPLIED_VERSION_TAG_ID),
        appliedVersionTagName = row.nullableString(APPLIED_VERSION_TAG_NAME),
        appliedVersionTagType = row.nullableInt(APPLIED_VERSION_TAG_TYPE),
    )

    private fun QueryRow.requiredInt(alias: String, failureMessage: String): Int =
        requireNotNull(this[alias] as? Int) { failureMessage }

    private fun QueryRow.requiredString(alias: String, failureMessage: String): String =
        requireNotNull(this[alias] as? String) { failureMessage }

    private fun QueryRow.nullableInt(alias: String): Int? {
        val value = this[alias] ?: return null
        return value as? Int ?: throw IllegalStateException("Status query returned a non-integer '$alias'")
    }

    private fun QueryRow.nullableString(alias: String): String? {
        val value = this[alias] ?: return null
        return value as? String ?: throw IllegalStateException("Status query returned a non-string '$alias'")
    }

    private companion object {
        const val TAG_ID = "tagId"
        const val TAG_NAME = "tagName"
        const val TAG_TYPE = "tagType"
        const val TAG_VERSION_ID = "tagVersionId"
        const val MIGRATION_HISTORY_ID = "migrationHistoryId"
        const val APPLIED_VERSION_REFERENCE_ID = "appliedVersionReferenceId"
        const val APPLIED_VERSION_ID = "appliedVersionId"
        const val APPLIED_VERSION_TAG_ID = "appliedVersionTagId"
        const val APPLIED_VERSION_TAG_NAME = "appliedVersionTagName"
        const val APPLIED_VERSION_TAG_TYPE = "appliedVersionTagType"
    }
}
