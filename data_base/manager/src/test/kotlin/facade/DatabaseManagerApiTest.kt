package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DatabaseManagerApiTest {
    private object Primary : DatabaseKey

    @Test
    fun concreteManagerAndHandleImplementConsumerContractsWithoutOpeningContext() {
        val concrete = DatabaseManager {
            database(Primary) {
                config = DatabaseConfig(
                    connection = DatabaseConnectionConfig(
                        dbDomain = "jdbc:mysql://127.0.0.1",
                        dbPort = 3306,
                        dbName = "manager_api_test",
                        dbUserName = "test-user",
                        dbPassword = "test-secret",
                    ),
                )
            }
        }
        val manager: DatabaseManagerApi = concrete

        try {
            val handle: DatabaseHandleApi = manager[Primary]
            assertIs<DatabaseManagerApi>(concrete)
            assertIs<DatabaseHandleApi>(handle)
            assertEquals(Primary, handle.key)
            assertEquals(listOf(Primary), manager.registeredKeys().toList())
        } finally {
            manager.shutdown()
        }
    }

    @Test
    fun handlesExposeConnectionTransactionAndMigrationButNoDirectSchemaExecution() {
        val interfaceMethods = DatabaseHandleApi::class.java.methods
        val concreteMethods = DatabaseHandle::class.java.methods

        assertTrue(interfaceMethods.any { it.name == "connection" })
        assertTrue(concreteMethods.any { it.name == "connection" })
        assertTrue(interfaceMethods.any { it.name == "transaction" })
        assertTrue(interfaceMethods.any { it.name == "migrate" })
        assertFalse(interfaceMethods.any { it.name == "query" })
        assertFalse(interfaceMethods.any { it.name == "schema" || it.name == "schemaWithQueryInfo" })
        assertFalse(concreteMethods.any { method ->
            method.name == "query" || method.name == "schema" || method.name == "schemaWithQueryInfo" ||
                method.parameterTypes.any { it.name == "gog.my_project.data_base_schema.SchemaOperation" }
        })
    }
}
