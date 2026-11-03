package gog.my_project.data_base_manager.example.examples

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.example.database.AnalyticsDatabase
import gog.my_project.data_base_manager.example.database.MainDatabase

/** Demonstrates separate handles and capabilities for the two database registrations. */
fun runMultiDatabaseExamples(manager: DatabaseManager) {
    val main = manager[MainDatabase]
    val analytics = manager[AnalyticsDatabase]

    report("Main database migration", migrateMainDatabase(main))
    report("Main database query", readMainCustomers(main))
    report("Main database transactional query", readMainCustomersInTransaction(main))
    report("Analytics schema operation", createAnalyticsEventsTable(analytics))
}

private fun report(label: String, result: ExecuteResult<*>) {
    when (result) {
        is ExecuteResult.Success -> println("$label: succeeded")
        is ExecuteResult.Failure -> println("$label: failed (${result.exception.message})")
    }
}
