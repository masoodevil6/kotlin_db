package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Opt-in integration test. Configure KOTLIN_DB_MIGRATION_TEST_* for a dedicated MySQL test database. */
class SystemMigrationStateStoreIntegrationTest {
    @Test
    fun bootstrapsReadsAndRecordsAgainstRealMySql() {
        val host = System.getenv("KOTLIN_DB_MIGRATION_TEST_HOST")
        val database = System.getenv("KOTLIN_DB_MIGRATION_TEST_DATABASE")
        val username = System.getenv("KOTLIN_DB_MIGRATION_TEST_USERNAME")
        val password = System.getenv("KOTLIN_DB_MIGRATION_TEST_PASSWORD")
        assumeTrue(!host.isNullOrBlank() && !database.isNullOrBlank() && !username.isNullOrBlank()) {
            "Set KOTLIN_DB_MIGRATION_TEST_HOST/DATABASE/USERNAME/PASSWORD to enable the disposable MySQL integration test"
        }

        val port = System.getenv("KOTLIN_DB_MIGRATION_TEST_PORT")?.toIntOrNull() ?: 3306
        val config = DatabaseConfig(
            dbDomain = "jdbc:mysql://$host",
            dbPort = port,
            dbName = database!!,
            dbUserName = username!!,
            dbPassword = password.orEmpty(),
            dialect = DialectQuery.MY_SQL,
        )
        val previousConfig = DefaultDatabaseConfig.config
        DefaultDatabaseConfig.config = config

        var connection: java.sql.Connection? = null
        var createdTestTable = false
        try {
            val testConnection = DriverManager.getConnection(config.getDbUrl(), config.dbUserName, config.dbPassword)
            connection = testConnection
            val existing = testConnection.metaData.getTables(testConnection.catalog, null, "system_migration", arrayOf("TABLE"))
            val tableAlreadyExists = existing.use { it.next() }
            assumeTrue(!tableAlreadyExists) {
                "The dedicated integration database already contains system_migration; refusing to touch existing data"
            }

            val manager = QueryExecute()
            val store = SystemMigrationStateStore(manager, MigrationExecutor(queryExecutor = manager))
            createdTestTable = true
            val initial = store.initializeBlocking()
            assertTrue(initial.history.isEmpty())
            assertEquals(null, initial.currentBatch)

            val secondBootstrap = store.initializeBlocking()
            assertTrue(secondBootstrap.history.isEmpty())

            val migrationId = "integration_${UUID.randomUUID()}"
            store.recordBlocking(migrationId, 1)
            val secondMigrationId = "integration_${UUID.randomUUID()}"
            store.recordBlocking(secondMigrationId, 3)
            val recorded = store.initializeBlocking()
            assertEquals(listOf(migrationId, secondMigrationId), recorded.history.map { it.migration })
            assertEquals(3, recorded.currentBatch)

            var duplicateResult: ExecuteResult<Unit>? = null
            store.recordSuccessfulMigration(migrationId, 4) { duplicateResult = it }
            assertIs<ExecuteResult.Failure>(duplicateResult)

        } finally {
            if (createdTestTable) {
                connection?.createStatement()?.use { it.execute("DROP TABLE IF EXISTS `system_migration`") }
            }
            DefaultDatabaseConfig.config = previousConfig
            connection?.close()
        }
    }

    private fun SystemMigrationStateStore.initializeBlocking(): SystemMigrationState {
        var result: ExecuteResult<SystemMigrationState>? = null
        initialize { result = it }
        return (assertNotNull(result) as? ExecuteResult.Success)?.result
            ?: error("System migration state initialization failed: $result")
    }

    private fun SystemMigrationStateStore.recordBlocking(migration: String, batch: Int) {
        var result: ExecuteResult<Unit>? = null
        recordSuccessfulMigration(migration, batch) { result = it }
        assertEquals(ExecuteResult.Success(Unit), result)
    }
}
