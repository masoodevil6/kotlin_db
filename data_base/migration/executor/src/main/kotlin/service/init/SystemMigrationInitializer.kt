package gog.my_project.data_base_migration.executor.service.init

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationTable
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationTagTable
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationVersionTable
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_schema.Schema

/** Creates the system migration storage tables without running user migrations. */
class SystemMigrationInitializer {
    /** Creates tag, version, then migration storage in the caller's Operation scope. */
    fun init(scope: OperationExecutionScope): ExecuteResult<Boolean> = safely(scope) {
        val tagResult = Schema.execute(
            SystemMigrationTagTable.definition(),
            scope,
        )
        if (tagResult is ExecuteResult.Failure) return@safely tagResult

        val versionResult = Schema.execute(
            SystemMigrationVersionTable.definition(),
            scope,
        )
        if (versionResult is ExecuteResult.Failure) return@safely versionResult

        val migrationResult = Schema.execute(
            SystemMigrationTable.definition(),
            scope,
        )
        if (migrationResult is ExecuteResult.Failure) return@safely migrationResult

        ExecuteResult.Success(true)
    }

    private inline fun <T> safely(
        scope: OperationExecutionScope,
        block: () -> ExecuteResult<T>,
    ): ExecuteResult<T> = try {
        scope.rejectOperation()?.let { return ExecuteResult.Failure(it) }
        block().also(scope::recordResult)
    } catch (error: Error) {
        scope.recordFatal(error)
        throw error
    } catch (failure: Throwable) {
        scope.failed(failure)
    }
}