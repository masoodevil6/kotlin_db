package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.Migration
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base.migration.api.interfaces.MigrationDefinition
import gog.my_project.data_base.migration.api.interfaces.MigrationId
import gog.my_project.data_base.migration.api.interfaces.MigrationIdentity
import gog.my_project.data_base.migration.api.interfaces.RegisteredMigration
import gog.my_project.data_base.migration.api.interfaces.SingleMigration
import gog.my_project.data_base.migration.builder.createTable
import gog.my_project.data_base.migration.builder.migration
import gog.my_project.data_base.migration.params.data_types.IntType
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Opt-in end-to-end test. It requires a dedicated, disposable MySQL database. */
class MigrationMigratorIntegrationTest {
    @Test
    fun bootstrapsRunsRecordsAndSkipsMigrationOnSecondInvocation() {
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
        var mayDropSystemMigration = false
        var mayDropMigrationTable = false
        try {
            val testConnection = DriverManager.getConnection(config.getDbUrl(), config.dbUserName, config.dbPassword)
            connection = testConnection
            val existingTables = testConnection.metaData.getTables(
                testConnection.catalog,
                null,
                "%",
                arrayOf("TABLE"),
            )
            val existingNames = existingTables.use { rows ->
                buildSet { while (rows.next()) add(rows.getString("TABLE_NAME")) }
            }
            assumeTrue(existingNames.none { it.equals(SystemMigrationTableName, ignoreCase = true) }) {
                "The dedicated integration database already contains system_migration; refusing to touch existing data"
            }
            assumeTrue(existingNames.none { it.equals(IntegrationMigrationTable, ignoreCase = true) }) {
                "The dedicated integration database already contains $IntegrationMigrationTable; refusing to touch it"
            }
            mayDropSystemMigration = true

            val identity = MigrationIdentity.fromAnnotationValue(IntegrationMigrationId)
            val configuration = MigrationConfiguration(
                listOf(SingleMigration(RegisteredMigration(identity, IntegrationCreateTableMigration::class))),
            )
            val manager = QueryExecute()
            val migrator = MigrationMigrator(configuration, MigrationExecutor(queryExecutor = manager))

            mayDropMigrationTable = true
            assertEquals(ExecuteResult.Success(Unit), migrator.migrateBlocking())

            assertEquals(ExecuteResult.Success(Unit), migrator.migrateBlocking())
            testConnection.prepareStatement(
                "SELECT COUNT(*), MIN(batch), MAX(batch) FROM `$SystemMigrationTableName` WHERE migration = ?",
            ).use { statement ->
                statement.setString(1, IntegrationMigrationId)
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    assertEquals(1, rows.getInt(1))
                    assertEquals(1, rows.getInt(2))
                    assertEquals(1, rows.getInt(3))
                }
            }
            val migratedTable = testConnection.metaData.getTables(
                testConnection.catalog,
                null,
                IntegrationMigrationTable,
                arrayOf("TABLE"),
            )
            assertTrue(migratedTable.use { it.next() })
        } finally {
            if (mayDropMigrationTable) {
                connection?.createStatement()?.use { it.execute("DROP TABLE IF EXISTS `$IntegrationMigrationTable`") }
            }
            if (mayDropSystemMigration) {
                connection?.createStatement()?.use { it.execute("DROP TABLE IF EXISTS `$SystemMigrationTableName`") }
            }
            DefaultDatabaseConfig.config = previousConfig
            connection?.close()
        }
    }

    private fun MigrationMigrator.migrateBlocking(): ExecuteResult<Unit> {
        var result: ExecuteResult<Unit>? = null
        migrate { result = it }
        return assertNotNull(result, "MigrationMigrator did not complete")
    }

    @MigrationId(IntegrationMigrationId)
    class IntegrationCreateTableMigration : Migration {
        override fun up(): MigrationDefinition = migration {
            createTable {
                table { tableName(IntegrationMigrationTable) }
                addColumn {
                    name("id")
                    dataType(IntType())
                    notNull()
                }
            }
        }

        override fun down(): MigrationDefinition = migration { }
    }

    private companion object {
        const val IntegrationMigrationId = "v120_runner_integration"
        const val IntegrationMigrationTable = "v120_runner_integration_table"
        const val SystemMigrationTableName = "system_migration"
    }
}
