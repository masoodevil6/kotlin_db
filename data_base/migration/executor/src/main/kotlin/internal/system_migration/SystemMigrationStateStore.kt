package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.executor.interfaces.IMigrationExecutor
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor

/**
 * Internal coordinator for migration state persistence. It does not select or execute pending
 * migrations; callers record an identity only after the corresponding DDL reports success.
 */
internal class SystemMigrationStateStore(
    queryExecutor: IQueryExecute,
    private val migrationExecutor: IMigrationExecutor = MigrationExecutor(queryExecutor),
) {
    private val reader = SystemMigrationReader(queryExecutor)
    private val recorder = SystemMigrationRecorder(queryExecutor)

    fun initialize(block: (ExecuteResult<SystemMigrationState>) -> Unit) {
        migrationExecutor.execute(
            queryBuilder = SystemMigrationTable.definition(),
            blockExecute = { result ->
                when (result) {
                    is ExecuteResult.Failure -> block(result)
                    is ExecuteResult.Success<*> -> {
                        if (result.result != true) {
                            block(ExecuteResult.Failure(IllegalStateException("Could not bootstrap system_migration")))
                        } else {
                            reader.read(block)
                        }
                    }
                }
            },
        )
    }

    fun read(block: (ExecuteResult<SystemMigrationState>) -> Unit) {
        reader.read(block)
    }

    fun recordSuccessfulMigration(
        migration: String,
        batch: Int,
        block: (ExecuteResult<Unit>) -> Unit,
    ) {
        recorder.recordSuccessfulMigration(migration, batch, block)
    }
}
