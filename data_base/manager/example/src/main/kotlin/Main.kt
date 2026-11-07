package gog.my_project.data_base_manager.example

import gog.my_project.data_base_manager.example.configuration.databaseConfig
import gog.my_project.data_base_manager.example.configuration.requireDatabaseExampleEnvironment
import gog.my_project.data_base_manager.example.database.AnalyticsDatabase
import gog.my_project.data_base_manager.example.database.MainDatabase
import gog.my_project.data_base_manager.example.examples.runMultiDatabaseExamples
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi

fun main() {
    val databases: DatabaseManagerApi =
        DatabaseManager {
            database(MainDatabase) {
                config = databaseConfig("MAIN", "main_example")
            }
            database(AnalyticsDatabase) {
                config = databaseConfig("ANALYTICS", "analytics_example")
            }
        }

    try {
        println("Registered databases: ${databases.registeredKeys().joinToString { it::class.simpleName.orEmpty() }}")
        if (!System.getProperty("database.example.run", "false").toBooleanStrict()) {
            println("Database calls are disabled. Set -Pdatabase.example.run=true to opt in.")
            return
        }

        requireDatabaseExampleEnvironment()
        runMultiDatabaseExamples(databases)
    } finally {
        databases.shutdown()
    }
}
