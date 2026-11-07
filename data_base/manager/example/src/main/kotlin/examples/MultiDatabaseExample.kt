package gog.my_project.data_base_manager.example.examples

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import gog.my_project.data_base_manager.example.database.AnalyticsDatabase
import gog.my_project.data_base_manager.example.database.MainDatabase
import gog.my_project.data_base_query.executor.result.QueryRow

/** Demonstrates separate handles and capabilities for the two database registrations. */
fun runMultiDatabaseExamples(manager: DatabaseManagerApi) {
    val main = manager[MainDatabase]
    val analytics = manager[AnalyticsDatabase]

    reportCustomers("Main database query", readMainCustomers(main))
    reportCustomers("Main database transactional query", readMainCustomersInTransaction(main))
}

private fun reportCustomers(label: String, result: ExecuteResult<List<QueryRow>>) {
    when (result) {
        is ExecuteResult.Success -> println("$label: ${result.result.orEmpty()}")
        is ExecuteResult.Failure -> throw result.exception
    }
}
