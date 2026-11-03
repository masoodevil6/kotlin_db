package gog.my_project.data_base_manager.example

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_manager.example.database.AnalyticsDatabase
import gog.my_project.data_base_manager.example.database.MainDatabase
import gog.my_project.data_base_manager.example.migrations.mainDatabaseMigrations
import gog.my_project.data_base_manager.facade.DatabaseManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame

class DatabaseCatalogConsumerTest {
    @Test
    fun publicManagerDslRegistersIndependentKeysAndMigrationConfiguration() {
        val manager = DatabaseManager {
            database(MainDatabase) {
                config = config("main_example")
                migrations = mainDatabaseMigrations()
            }
            database(AnalyticsDatabase) {
                config = config("analytics_example")
            }
        }

        try {
            assertEquals(setOf(MainDatabase, AnalyticsDatabase), manager.registeredKeys())
            assertEquals(MainDatabase, manager[MainDatabase].key)
            assertEquals(AnalyticsDatabase, manager[AnalyticsDatabase].key)
            assertNotSame(manager[MainDatabase], manager[AnalyticsDatabase])
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
