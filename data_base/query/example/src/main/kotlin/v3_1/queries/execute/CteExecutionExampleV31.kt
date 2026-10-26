package gog.my_project.data_base.query.example.v3_1.queries.execute

import gog.my_project.data_base.query.example.v3_1.queries.IExampleV31
import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success

class CteExecutionExampleV31<T>(
    private val title: String,
    private val execution: (IQueryBuilderExecutor) -> ExecuteResult<ICollection<T>>,
    private val formatRow: (T) -> String,
) : IExampleV31 {
    override fun execute(queryManager: IQueryBuilderExecutor) {
        println("\n=============================================\nV3.1: direct CTE execution - $title")
        execution(queryManager)
            .success { rows ->
                rows.forEach { row -> println("row: ${formatRow(row)}") }
                rows
            }
            .error { failure -> println("error: $failure") }
    }
}
