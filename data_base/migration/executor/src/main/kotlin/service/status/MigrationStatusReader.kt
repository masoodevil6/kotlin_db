package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.core.system_migration.cte.SystemMigrationStatusQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.executor.transaction.OperationQueryRows
import gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base_query.renderer.manager.DialectSelector
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import model.migration_status.MigrationStatusRow
import model.migration_status.MigrationStatusSnapshot

/** Reads the combined System Migration status CTE through the caller-owned Operation scope. */
internal class MigrationStatusReader {
    fun read(scope: OperationExecutionScope): ExecuteResult<MigrationStatusSnapshot> {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }

        return try {
            val query = SystemMigrationStatusQuery()
            val ast = query.createCte(SystemMigrationStatusQuery.RelationFilters()).definition.ast
            when (val rendered = QueryParameterRendering.render(
                ast,
                DialectSelector().select(scope.databasePlatformConfig.dialect),
            )) {
                is IdentityAwareQueryRenderResult -> {
                    scope.executeRead(
                        rendered.displaySql,
                        emptyList(),
                        ::readRows,
                    ).mapSnapshot()
                }
                is LegacyQueryRenderResult -> scope.executeRead(
                    requireNotNull(rendered.sql) { "System Migration status query rendered no SQL" },
                    emptyList(),
                    ::readRows,
                ).mapSnapshot()
            }
        } catch (error: Error) {
            scope.recordFatal(error)
            throw error
        } catch (failure: Throwable) {
            scope.failed(failure)
        }
    }

    private fun readRows(rows: OperationQueryRows): MigrationStatusSnapshot = MigrationStatusSnapshot(
        buildList {
            while (rows.next()) {
                add(
                    MigrationStatusRow(
                        tagId = requireNotNull(rows.getInt(TAG_ID)) { "Status query returned a null tagId" },
                        tagName = requireNotNull(rows.getString(TAG_NAME)) { "Status query returned a null tagName" },
                        tagType = requireNotNull(rows.getInt(TAG_TYPE)) { "Status query returned a null tagType" },
                        tagVersionId = rows.getInt(TAG_VERSION_ID),
                        migrationHistoryId = rows.getInt(MIGRATION_HISTORY_ID),
                        appliedVersionReferenceId = rows.getInt(APPLIED_VERSION_REFERENCE_ID),
                        appliedVersionId = rows.getInt(APPLIED_VERSION_ID),
                        appliedVersionTagId = rows.getInt(APPLIED_VERSION_TAG_ID),
                        appliedVersionTagName = rows.getString(APPLIED_VERSION_TAG_NAME),
                        appliedVersionTagType = rows.getInt(APPLIED_VERSION_TAG_TYPE),
                    ),
                )
            }
        },
    )

    private fun ExecuteResult<MigrationStatusSnapshot>.mapSnapshot(): ExecuteResult<MigrationStatusSnapshot> = when (this) {
        is ExecuteResult.Success -> ExecuteResult.Success(
            requireNotNull(result) { "System Migration status query returned no snapshot" },
        )
        is ExecuteResult.Failure -> ExecuteResult.Failure(exception)
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
