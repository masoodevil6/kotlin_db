package gog.my_project.data_base_migration.executor.controller

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.executor.service.init.SystemMigrationInitializer
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope


/** Public entry point for commands provided by the Migration executor. */
class MigrationController {
    private val initializer = SystemMigrationInitializer()

    /** Initializes the System Migration storage tables in the caller's Operation scope. */
    fun init(scope: OperationExecutionScope): ExecuteResult<Boolean> = initializer.init(scope)
}
