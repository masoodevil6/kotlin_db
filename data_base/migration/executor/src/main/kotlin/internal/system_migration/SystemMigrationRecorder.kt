package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder

/** Validates and records one successfully applied migration. */
internal class SystemMigrationRecorder(
    private val queryExecutor: IQueryExecute,
) {
    fun recordSuccessfulMigration(
        migration: String,
        batch: Int,
        block: (ExecuteResult<Unit>) -> Unit,
    ) {
        if (migration.isBlank()) {
            block(ExecuteResult.Failure(IllegalArgumentException("Migration identity must not be blank")))
            return
        }
        if (batch <= 0) {
            block(ExecuteResult.Failure(IllegalArgumentException("Migration batch must be positive")))
            return
        }

        val queryBuilder = QueryRenderInsertBuilder()
        queryBuilder.table { table(SystemMigrationTable.NAME, "") }
        queryBuilder.addValue { column("migration", migration) }
        queryBuilder.addValue { column("batch", batch) }

        queryExecutor.executeUpdate(queryBuilder.toBuiltQuery()) { result ->
            when (result) {
                is ExecuteResult.Failure -> block(result)
                is ExecuteResult.Success<*> -> {
                    if ((result.result ?: 0) == 1) {
                        block(ExecuteResult.Success(Unit))
                    } else {
                        block(ExecuteResult.Failure(
                            IllegalStateException("Expected one system_migration row to be inserted"),
                        ))
                    }
                }
            }
        }
    }
}
