package gog.my_project.data_base_schema.example.operations

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.executor.manager.SchemaExecutor

/** Executes one already-built public Schema operation using the library's SchemaExecutor. */
fun executeSchemaOperation(
    executor: SchemaExecutor,
    operation: ISchemaApi<*>,
    onResult: (ExecuteResult<Boolean>) -> Unit,
) {
    executor.execute(
        operation = operation,
        blockExecute = onResult,
        blockQueryInfo = { sql, params ->
            println("SQL: $sql")
            println("Parameters: $params")
        },
    )
}
