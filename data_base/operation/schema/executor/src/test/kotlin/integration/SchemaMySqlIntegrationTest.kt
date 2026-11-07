package gog.my_project.data_base_schema.executor.integration

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.builder.ast.create_index.SchemaCreateIndexBuilder
import gog.my_project.data_base_schema.builder.ast.create_table.render_schema_create_table.SchemaRenderCreateTableBuilder
import gog.my_project.data_base_schema.builder.ast.drop_index.SchemaDropIndexBuilder
import gog.my_project.data_base_schema.builder.ast.drop_table.SchemaDropTableBuilder
import gog.my_project.data_base_schema.executor.manager.SchemaExecutor
import gog.my_project.data_base_schema.params.data_types.IntType
import gog.my_project.data_base_schema.params.data_types.VarcharType
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/** Real MySQL DDL tests opt in through local Gradle properties and only use a qb_tx_test_* schema. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("mysql-test-schema")
class SchemaMySqlIntegrationTest {
    @Test
    fun createsAltersIndexesAndDropsOnlyItsGeneratedTable() {
        val settings = settingsOrSkip()
        val table = "qb_schema_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val index = "idx_${table}_email"
        val context = settings.context()
        val executor = SchemaExecutor(databaseContext = context)

        var safeToCleanup = false
        try {
            assumeTrue(!tableExists(settings, table)) {
                "A generated integration-test table name already exists; refusing to touch it"
            }
            assertEquals(true, execute(executor, createTable(table)))
            safeToCleanup = true
            assertTrue(tableExists(settings, table))

            assertEquals(true, execute(executor, SchemaAddColumnBuilder()
                .tableName(table)
                .name("notes")
                .dataType(VarcharType(100))
                .isNullable(true)))
            assertTrue(columnExists(settings, table, "notes"))

            assertEquals(true, execute(executor, SchemaCreateIndexBuilder()
                .tableName(table)
                .name(index)
                .column("email")))
            assertTrue(indexExists(settings, table, index))

            assertEquals(true, execute(executor, SchemaDropIndexBuilder()
                .tableName(table)
                .name(index)))
            assertTrue(!indexExists(settings, table, index))

            assertEquals(true, execute(executor, SchemaDropTableBuilder().tableName(table)))
            assertTrue(!tableExists(settings, table))
        } finally {
            try {
                context.close()
            } finally {
                if (safeToCleanup) {
                    settings.openConnection().use { connection ->
                        connection.createStatement().use { it.execute("DROP TABLE IF EXISTS `$table`") }
                    }
                }
            }
        }
    }

    @Test
    fun mysqlCreateTableImplicitlyCommitsEvenWhenTheOperationScopeLaterRollsBack() {
        val settings = settingsOrSkip()
        val table = "qb_schema_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val context = settings.context()
        val executor = SchemaExecutor(databaseContext = context)
        val forcedFailure = IllegalStateException("force Operation transaction rollback after MySQL DDL")

        var safeToCleanup = false
        try {
            assumeTrue(!tableExists(settings, table)) {
                "A generated integration-test table name already exists; refusing to touch it"
            }
            val result = executor.transaction { transaction ->
                var ddlResult: ExecuteResult<Boolean>? = null
                transaction.execute(createTable(table), blockExecute = { ddlResult = it })
                when (val executed = requireNotNull(ddlResult)) {
                    is ExecuteResult.Failure -> executed
                    is ExecuteResult.Success -> {
                        safeToCleanup = true
                        ExecuteResult.Failure(forcedFailure)
                    }
                }
            }

            assertSameFailure(forcedFailure, assertIs<ExecuteResult.Failure>(result).exception)
            // MySQL CREATE TABLE causes an implicit commit; Operation's rollback cannot undo it.
            assertTrue(tableExists(settings, table))
        } finally {
            try {
                context.close()
            } finally {
                if (safeToCleanup) {
                    settings.openConnection().use { connection ->
                        connection.createStatement().use { it.execute("DROP TABLE IF EXISTS `$table`") }
                    }
                }
            }
        }
    }

    private fun settingsOrSkip(): MySqlSettings {
        val enabled = System.getProperty(ENABLED)
        val host = System.getProperty(HOST)
        val port = System.getProperty(PORT)?.toIntOrNull()
        val schema = System.getProperty(NAME)
        val username = System.getProperty(USERNAME)
        val password = System.getProperty(PASSWORD)
        assumeTrue(
            enabled == "true" && !host.isNullOrBlank() && port != null && !schema.isNullOrBlank() &&
                !username.isNullOrBlank() && !password.isNullOrEmpty(),
        ) {
            "Set execute.test.db.transaction.enabled=true and all execute.test.db connection properties to enable MySQL integration tests"
        }
        assumeTrue(host in LOCAL_HOSTS) { "Real database integration tests only accept a local MySQL host" }
        assumeTrue(port in 1..65535) { "execute.test.db.port must be a valid TCP port" }
        assumeTrue(TEST_SCHEMA.matches(schema!!)) {
            "Real database integration tests require a pre-created schema named qb_tx_test_<suffix>"
        }
        assumeTrue(!username.equals("root", ignoreCase = true)) {
            "Real database integration tests require a dedicated non-root test user"
        }
        return MySqlSettings(host!!, port!!, schema, username!!, password!!)
    }

    private fun createTable(name: String) = SchemaRenderCreateTableBuilder().apply {
        table { tableName(name) }
        addColumn {
            name("id")
            dataType(IntType())
            notNull()
            autoIncrement()
            primaryKey()
        }
        addColumn {
            name("email")
            dataType(VarcharType(255))
            notNull()
        }
    }

    private fun execute(executor: SchemaExecutor, operation: gog.my_project.data_base_schema.api.interfaces.ISchemaApi<*>): Boolean {
        var result: ExecuteResult<Boolean>? = null
        executor.execute(operation, { result = it })
        return assertIs<ExecuteResult.Success<Boolean>>(requireNotNull(result)).result
            ?: error("Schema execution returned no Boolean result")
    }

    private fun tableExists(settings: MySqlSettings, table: String): Boolean =
        settings.openConnection().use { connection ->
            connection.metaData.getTables(connection.catalog, null, table, arrayOf("TABLE")).use { it.next() }
        }

    private fun columnExists(settings: MySqlSettings, table: String, column: String): Boolean =
        settings.openConnection().use { connection ->
            connection.metaData.getColumns(connection.catalog, null, table, column).use { it.next() }
        }

    private fun indexExists(settings: MySqlSettings, table: String, index: String): Boolean =
        settings.openConnection().use { connection ->
            connection.metaData.getIndexInfo(connection.catalog, null, table, false, false).use { rows ->
                buildList { while (rows.next()) add(rows.getString("INDEX_NAME")) }.any { it == index }
            }
        }

    private fun assertSameFailure(expected: Throwable, actual: Throwable) {
        assertTrue(expected === actual, "The original Operation failure should be returned")
    }

    private data class MySqlSettings(
        val host: String,
        val port: Int,
        val schema: String,
        val username: String,
        val password: String,
    ) {
        fun openConnection(): Connection = DriverManager.getConnection(
            "jdbc:mysql://$host:$port/$schema?serverTimezone=UTC",
            username,
            password,
        )

        fun context() = DatabaseContext(
            name = "schema-mysql-integration",
            config = DatabaseConfig(
                dbDomain = "jdbc:mysql://$host",
                dbPort = port,
                dbName = "$schema?serverTimezone=UTC",
                dbUserName = username,
                dbPassword = password,
                dialect = DialectQuery.MY_SQL,
                targetDatabaseVersion = MYSQL.Version(8, 0, 36),
            ),
        )
    }

    private companion object {
        const val ENABLED = "execute.test.db.transaction.enabled"
        const val HOST = "execute.test.db.host"
        const val PORT = "execute.test.db.port"
        const val NAME = "execute.test.db.name"
        const val USERNAME = "execute.test.db.username"
        const val PASSWORD = "execute.test.db.password"
        val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "::1")
        val TEST_SCHEMA = Regex("^qb_tx_test_[A-Za-z0-9_]+$")
    }
}
