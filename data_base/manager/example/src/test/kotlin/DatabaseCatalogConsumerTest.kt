package gog.my_project.data_base_manager.example

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_manager.example.database.AnalyticsDatabase
import gog.my_project.data_base_manager.example.database.MainDatabase
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseManagerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class DatabaseCatalogConsumerTest {
    @Test
    fun publicManagerDslRegistersIndependentKeys() {
        val manager: DatabaseManagerApi = DatabaseManager {
            database(MainDatabase) {
                config = config("main_example")
            }
            database(AnalyticsDatabase) {
                config = config("analytics_example")
            }
        }

        try {
            assertEquals(setOf(MainDatabase, AnalyticsDatabase), manager.registeredKeys())
            val main: DatabaseHandleApi = manager[MainDatabase]
            val analytics: DatabaseHandleApi = manager[AnalyticsDatabase]
            assertEquals(MainDatabase, main.key)
            assertEquals(AnalyticsDatabase, analytics.key)
            assertNotSame(main, analytics)
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun publicManagerDslRejectsDuplicateTypedKeys() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseManager {
                database(MainDatabase) { config = config("main_example") }
                database(MainDatabase) { config = config("other_example") }
            }
        }
    }

    private fun config(databaseName: String) = DatabaseConfig(
        connection = DatabaseConnectionConfig(
            dbDomain = "jdbc:mysql://127.0.0.1",
            dbPort = 3306,
            dbName = databaseName,
            dbUserName = "example-user",
            dbPassword = "",
        ),
    )
}
