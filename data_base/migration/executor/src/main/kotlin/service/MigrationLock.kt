package gog.my_project.data_base_migration.executor.service

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope

internal fun <T> withMigrationLock(
    scope: OperationExecutionScope,
    block: (OperationExecutionScope) -> ExecuteResult<T>,
): ExecuteResult<T> = scope.withMigrationFailureStatusCapability(60) {
    block(scope)
}
