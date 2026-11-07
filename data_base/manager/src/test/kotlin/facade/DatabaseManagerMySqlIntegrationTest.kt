package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.MYSQL
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_query.ast.enums.DataType
import gog.my_project.data_base_query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base_query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base_query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base_query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/** Opt-in end-to-end coverage of Manager Query paths on a dedicated MySQL test schema. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("mysql-test-schema")
class DatabaseManagerMySqlIntegrationTest {
    @Test
    fun managerExecutesQueryCrudAndTransactionsAgainstMySql() {
        val settings = settingsOrSkip()
        val queryTable = "qb_mgr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val config = settings.databaseConfig()
        val manager = DatabaseManager {
            database(TestDatabase) {
                this.config = config
            }
        }

        var safeToCleanup = false
        try {
            settings.openConnection().use { connection ->
                val tablesBefore = tableNames(connection)
                assumeTrue(tablesBefore.none { it.equals(queryTable, ignoreCase = true) }) {
                    "A generated integration-test table name already exists; refusing to touch it"
                }
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE `$queryTable` (id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, " +
                            "marker VARCHAR(120) NOT NULL) ENGINE=InnoDB",
                    )
                }
                safeToCleanup = true
            }

            val database = manager[TestDatabase]
            val connectionResult = database.connection { executor ->
                when (val inserted = executor.queryBuilder(insertQuery(queryTable, "before-update")).execute()) {
                    is ExecuteResult.Failure -> inserted
                    is ExecuteResult.Success -> when (
                        val updated = executor.queryBuilder(updateQuery(queryTable, "before-update", "after-update")).execute()
                    ) {
                        is ExecuteResult.Failure -> updated
                        is ExecuteResult.Success -> when (
                            val selected = executor.queryBuilder(selectQuery(queryTable)).execute()
                        ) {
                            is ExecuteResult.Failure -> selected
                            is ExecuteResult.Success -> {
                                val marker = assertNotNull(selected.result).single()["marker"]
                                if (marker != "after-update") {
                                    ExecuteResult.Failure(AssertionError("Manager Query update/read returned an unexpected value"))
                                } else {
                                    executor.queryBuilder(deleteQuery(queryTable, "after-update")).execute()
                                        .let { deleted ->
                                            when (deleted) {
                                                is ExecuteResult.Failure -> deleted
                                                is ExecuteResult.Success -> ExecuteResult.Success(Unit)
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
            }
            assertIs<ExecuteResult.Success<Unit>>(connectionResult)

            val committed = database.transaction { transaction ->
                transaction.queryBuilder(insertQuery(queryTable, "committed")).execute()
                    .let { inserted ->
                        when (inserted) {
                            is ExecuteResult.Failure -> inserted
                            is ExecuteResult.Success -> ExecuteResult.Success(Unit)
                        }
                    }
            }
            assertIs<ExecuteResult.Success<Unit>>(committed)

            val rollbackMarker = IllegalStateException("rollback integration row")
            val rolledBack = database.transaction { transaction ->
                when (val inserted = transaction.queryBuilder(insertQuery(queryTable, "rolled-back")).execute()) {
                    is ExecuteResult.Failure -> inserted
                    is ExecuteResult.Success -> ExecuteResult.Failure(rollbackMarker)
                }
            }
            assertIs<ExecuteResult.Failure>(rolledBack).also { assertTrue(it.exception === rollbackMarker) }
            assertEquals(listOf("committed"), readMarkers(settings, queryTable))
        } finally {
            try {
                manager.shutdown()
            } finally {
                if (safeToCleanup) {
                    settings.openConnection().use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("DROP TABLE IF EXISTS `$queryTable`")
                        }
                    }
                }
            }
        }
    }

    private fun settingsOrSkip(): MySqlSettings {
        val enabled = System.getProperty(ENABLED)
        val host = System.getProperty(HOST)
        val port = System.getProperty(PORT)?.toIntOrNull()
        val database = System.getProperty(NAME)
        val username = System.getProperty(USERNAME)
        val password = System.getProperty(PASSWORD)
        assumeTrue(
            enabled == "true" && !host.isNullOrBlank() && port != null && !database.isNullOrBlank() &&
                !username.isNullOrBlank() && !password.isNullOrEmpty(),
        ) {
            "Set execute.test.db.transaction.enabled=true and all execute.test.db connection properties to enable MySQL integration tests"
        }
        assumeTrue(host in LOCAL_HOSTS) { "Real database integration tests only accept a local MySQL host" }
        assumeTrue(port in 1..65535) { "execute.test.db.port must be a valid TCP port" }
        assumeTrue(TEST_SCHEMA.matches(database!!)) {
            "Real database integration tests require a pre-created schema named qb_tx_test_<suffix>"
        }
        assumeTrue(!username.equals("root", ignoreCase = true)) {
            "Real database integration tests require a dedicated non-root test user"
        }
        return MySqlSettings(host!!, port!!, database, username!!, password!!)
    }

    private fun insertQuery(table: String, marker: String) = QueryRenderInsertBuilder().apply {
        table { table(table) }
        addValue {
            column("marker")
            setValue {
                paramName("marker")
                paramValue(marker)
            }
        }
    }

    private fun updateQuery(table: String, oldValue: String, newValue: String) = QueryRenderUpdateBuilder()
        .table { table(table) }
        .addValue {
            column("marker")
            setValue {
                paramName("new_marker")
                paramValue(newValue)
            }
        }
        .where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("marker") }
                    operationEqual()
                    sideValueParam { paramName("marker"); paramValue(oldValue) }
                }
            }
        }

    private fun deleteQuery(table: String, marker: String) = QueryRenderDeleteBuilder()
        .table { table(table) }
        .where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("marker") }
                    operationEqual()
                    sideValueParam { paramName("marker"); paramValue(marker) }
                }
            }
        }

    private fun selectQuery(table: String) = QueryRenderSelectBuilder()
        .select {
            addColumn {
                column { tableAttribute("id") }
                alias("id")
                execute(DataType.LONG)
            }
            addColumn {
                column { tableAttribute("marker") }
                alias("marker")
                execute(DataType.STRING)
            }
        }
        .from { table(table).alias("manager_rows") }

    private fun tableNames(connection: Connection): Set<String> =
        connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE")).use { rows ->
            buildSet { while (rows.next()) add(rows.getString("TABLE_NAME")) }
        }

    private fun readMarkers(settings: MySqlSettings, table: String): List<String> =
        settings.openConnection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT marker FROM `$table` ORDER BY id").use { rows ->
                    buildList { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    private data class MySqlSettings(
        val host: String,
        val port: Int,
        val schema: String,
        val username: String,
        val password: String,
    ) {
        fun databaseConfig() = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://$host",
                dbPort = port,
                dbName = "$schema?serverTimezone=UTC",
                dbUserName = username,
                dbPassword = password,
            ),
            platform = DatabasePlatformConfig(DialectQuery.MY_SQL, MYSQL.Version(8, 0, 36)),
        )

        fun openConnection(): Connection = DriverManager.getConnection(
            "jdbc:mysql://$host:$port/$schema?serverTimezone=UTC",
            username,
            password,
        )
    }

    private object TestDatabase : DatabaseKey

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
