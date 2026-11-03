package gog.my_project.data_base_schema.example.operations

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionContext
import gog.my_project.data_base_schema.Schema
import gog.my_project.data_base_schema.SchemaOperation

/** Executes a Schema operation in an existing Operation-owned connection or transaction scope. */
fun executeSchemaOperation(
    operation: SchemaOperation,
    scope: OperationExecutionContext,
): ExecuteResult<Boolean> = Schema.execute(operation, scope) { sql, parameters ->
    println("SQL: $sql")
    println("Parameters: $parameters")
}

/** Independent execution delegates connection ownership to Operation. */
fun executeSchemaOperationIndependently(
    operation: SchemaOperation,
): ExecuteResult<Boolean> = Schema.execute(operation) { sql, parameters ->
    println("SQL: $sql")
    println("Parameters: $parameters")
}
