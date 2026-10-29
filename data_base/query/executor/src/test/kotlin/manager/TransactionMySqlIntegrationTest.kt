package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.result.QueryRow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/**
 * Destructive integration tests are opt-in and are restricted to a pre-created test schema whose name
 * starts with qb_tx_test_ or is the explicitly allowlisted kotlin_db_test_connection schema. Every test
 * owns one generated InnoDB table and drops only that table.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("DefaultDatabaseConfig.config")
class TransactionMySqlIntegrationTest {

    @Test
    fun no_opt_in_produces_no_database_settings_or_connection_attempt() {
        val attempts = mutableListOf<String>()
        val settings = readSettings(Properties())

        if (settings != null) {
            attempts += settings.schema
            openVerifiedConnection(settings).close()
        }

        assertNull(settings)
        assertEquals(emptyList(), attempts)
    }

    @Test
    fun enabled_configuration_requires_all_five_connection_properties_and_test_schema() {
        val incomplete = Properties().apply {
            setProperty(ENABLED_PROPERTY, "true")
            setProperty(HOST_PROPERTY, "localhost")
            setProperty(NAME_PROPERTY, "qb_tx_test_missing_port")
            setProperty(USERNAME_PROPERTY, "qb_tx_test_user")
            setProperty(PASSWORD_PROPERTY, "")
        }

        val missingProperty = assertFailsWith<IllegalStateException> {
            readSettings(incomplete)
        }
        assertTrue(missingProperty.message.orEmpty().contains(PORT_PROPERTY))

        val prefixedTestSchema = readSettings(
            completeProperties(
                host = "localhost",
                port = "3306",
                schema = "qb_tx_test_valid_1",
                username = "qb_tx_test_user",
                password = "",
            ),
        )
        assertEquals("qb_tx_test_valid_1", prefixedTestSchema?.schema)

        val explicitlyAllowedSchema = readSettings(
            completeProperties(
                host = "localhost",
                port = "3306",
                schema = "kotlin_db_test_connection",
                username = "qb_tx_test_user",
                password = "",
            ),
        )
        assertEquals("kotlin_db_test_connection", explicitlyAllowedSchema?.schema)

        val invalidSchema = completeProperties(
            host = "localhost",
            port = "3306",
            schema = "application_database",
            username = "qb_tx_test_user",
            password = "",
        )
        val schemaFailure = assertFailsWith<IllegalArgumentException> {
            readSettings(invalidSchema)
        }
        assertTrue(schemaFailure.message.orEmpty().contains("qb_tx_test_"))
    }

    @Test
    fun commit_makes_multiple_changes_visible_to_a_fresh_connection() = withTestTable { settings, table ->
        val result = QueryBuilderExecutor().transaction { tx ->
            val first = tx.queryBuilder(insertQuery(table, "commit_first")).execute()
            if (first is ExecuteResult.Failure) {
                first
            } else {
                val second = tx.queryBuilder(insertQuery(table, "commit_second")).execute()
                if (second is ExecuteResult.Failure) second else ExecuteResult.Success("committed")
            }
        }

        assertEquals("committed", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(listOf("commit_first", "commit_second"), readMarkers(settings, table))
    }

    @Test
    fun real_sql_failure_rolls_back_prior_change_and_preserves_driver_error_and_cleanup() =
        withTestTable { settings, table ->
            val probe = JdbcResourceProbe()
            val executor = QueryBuilderExecutor {
                probe.wrap(openVerifiedConnection(settings))
            }
            var queryFailure: Throwable? = null

            val result = executor.transaction { tx ->
                val inserted = tx.queryBuilder(insertQuery(table, "rollback_me")).execute()
                if (inserted is ExecuteResult.Failure) {
                    queryFailure = inserted.exception
                    inserted
                } else {
                    val duplicate = tx.queryBuilder(insertQuery(table, "rollback_me")).execute()
                    if (duplicate is ExecuteResult.Failure) queryFailure = duplicate.exception
                    duplicate
                }
            }

            val failure = assertIs<ExecuteResult.Failure>(result).exception
            assertSame(queryFailure, failure)
            val sqlFailure = assertIs<SQLException>(failure)
            assertEquals("23000", sqlFailure.sqlState, sqlFailure.toString())
            assertEquals(emptyList(), readMarkers(settings, table))
            assertEquals(1, probe.rollbackCalls)
            assertEquals(1, probe.connectionCloseCalls)
            assertEquals(2, probe.statementCloseCalls)
            assertEquals(1, probe.generatedKeyResultSetCloseCalls)
        }

    @Test
    fun ignored_query_failure_still_prevents_commit_and_is_visible_as_failure() =
        withTestTable { settings, table ->
            val result = QueryBuilderExecutor().transaction { tx ->
                val inserted = tx.queryBuilder(insertQuery(table, "ignored_failure")).execute()
                if (inserted is ExecuteResult.Failure) {
                    inserted
                } else {
                    val ignored = tx.queryBuilder(insertQuery(table, "ignored_failure")).execute()
                    assertIs<ExecuteResult.Failure>(ignored)
                    ExecuteResult.Success("callback ignored the SQL failure")
                }
            }

            val failure = assertIs<ExecuteResult.Failure>(result).exception
            assertIs<SQLException>(failure)
            assertEquals(emptyList(), readMarkers(settings, table))
        }

    @Test
    fun multiple_queries_use_one_transaction_connection_and_close_it_once() = withTestTable { settings, table ->
        val probe = JdbcResourceProbe()
        var connectionAcquisitions = 0
        val executor = QueryBuilderExecutor {
            connectionAcquisitions++
            probe.wrap(openVerifiedConnection(settings))
        }

        val result = executor.transaction { tx ->
            val first = tx.queryBuilder(insertQuery(table, "same_connection_first")).execute()
            if (first is ExecuteResult.Failure) {
                first
            } else {
                val second = tx.queryBuilder(insertQuery(table, "same_connection_second")).execute()
                if (second is ExecuteResult.Failure) second else ExecuteResult.Success(Unit)
            }
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertEquals(1, connectionAcquisitions)
        assertEquals(2, probe.statementConnectionIds.size)
        assertEquals(setOf(1), probe.statementConnectionIds.toSet())
        assertEquals(2, probe.statementCloseCalls)
        assertEquals(1, probe.commitCalls)
        assertEquals(0, probe.rollbackCalls)
        assertEquals(1, probe.connectionCloseCalls)
        assertEquals(
            listOf("same_connection_first", "same_connection_second"),
            readMarkers(settings, table),
        )
    }

    @Test
    fun select_is_materialized_before_result_resources_and_transaction_connection_close() =
        withTestTable { settings, table ->
            val probe = JdbcResourceProbe()
            val executor = QueryBuilderExecutor {
                probe.wrap(openVerifiedConnection(settings))
            }
            var resourcesWereClosedBeforeCallbackReturned = false

            val result = executor.transaction { tx ->
                val inserted = tx.queryBuilder(insertQuery(table, "materialized")).execute()
                if (inserted is ExecuteResult.Failure) {
                    inserted
                } else {
                    when (val selected = tx.queryBuilder(selectQuery(table)).execute()) {
                        is ExecuteResult.Failure -> selected
                        is ExecuteResult.Success -> {
                            resourcesWereClosedBeforeCallbackReturned =
                                probe.selectResultSetCloseCalls == 1 &&
                                    probe.generatedKeyResultSetCloseCalls == 1 &&
                                    probe.statementCloseCalls == 2 &&
                                    probe.connectionCloseCalls == 0
                            ExecuteResult.Success<List<QueryRow>>(requireNotNull(selected.result))
                        }
                    }
                }
            }

            val rows = assertNotNull(assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result)
            assertEquals(1, rows.size)
            assertEquals("materialized", rows.single()["marker"])
            assertTrue(resourcesWereClosedBeforeCallbackReturned)
            assertEquals(1, probe.selectResultSetCloseCalls)
            assertEquals(1, probe.generatedKeyResultSetCloseCalls)
            assertEquals(2, probe.statementCloseCalls)
            assertEquals(1, probe.commitCalls)
            assertEquals(1, probe.connectionCloseCalls)
        }

    @Test
    fun mysql_for_update_blocks_a_second_connection_until_first_transaction_commits() {
        assertForUpdateWaitsUntilFirstTransactionEnds(rollbackFirst = false)
    }

    @Test
    fun mysql_for_update_blocks_a_second_connection_until_first_transaction_rolls_back() {
        assertForUpdateWaitsUntilFirstTransactionEnds(rollbackFirst = true)
    }

    private fun assertForUpdateWaitsUntilFirstTransactionEnds(rollbackFirst: Boolean) =
        withTestTable { settings, table ->
            val rowId = insertLockTestRow(settings, table)
            val firstTransactionHasLock = CountDownLatch(1)
            val releaseFirstTransaction = CountDownLatch(1)
            val secondSelectEnteredJdbc = CountDownLatch(1)
            val firstConnection = AtomicReference<Connection?>()
            val secondConnection = AtomicReference<Connection?>()
            val secondRows = AtomicReference<List<QueryRow>?>()
            val workerPool = Executors.newFixedThreadPool(2) { task ->
                Thread(task, "mysql-for-update-contention").apply { isDaemon = true }
            }
            val secondProbe = JdbcResourceProbe(onExecuteQuery = secondSelectEnteredJdbc)
            val firstExecutor = QueryBuilderExecutor {
                openVerifiedConnection(settings).also(firstConnection::set)
            }
            val secondExecutor = QueryBuilderExecutor {
                val connection = openVerifiedConnection(settings)
                try {
                    connection.createStatement().use { statement ->
                        statement.execute("SET SESSION innodb_lock_wait_timeout = $MYSQL_LOCK_WAIT_SECONDS")
                    }
                    secondConnection.set(connection)
                    secondProbe.wrap(connection)
                } catch (failure: Throwable) {
                    try {
                        connection.close()
                    } catch (cleanupFailure: Throwable) {
                        if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
                    }
                    throw failure
                }
            }
            val requestedRollback = IllegalStateException("release FOR UPDATE row lock by rollback")
            val firstTask = workerPool.submit<ExecuteResult<Unit>> {
                firstExecutor.transaction { tx ->
                    when (val selected = tx.queryBuilder(lockingSelectQuery(table, rowId)).execute()) {
                        is ExecuteResult.Failure -> selected
                        is ExecuteResult.Success -> {
                            val rows = selected.result
                                ?: return@transaction ExecuteResult.Failure(
                                    IllegalStateException("Locking SELECT returned a null row list"),
                                )
                            if (rows.size != 1 || rows.single()["id"] != rowId) {
                                return@transaction ExecuteResult.Failure(
                                    IllegalStateException("Locking SELECT did not return the requested row"),
                                )
                            }
                            firstTransactionHasLock.countDown()
                            if (!releaseFirstTransaction.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                ExecuteResult.Failure(TimeoutException("Timed out waiting to release the first row lock"))
                            } else if (rollbackFirst) {
                                ExecuteResult.Failure(requestedRollback)
                            } else {
                                ExecuteResult.Success(Unit)
                            }
                        }
                    }
                }
            }
            var secondTask: Future<ExecuteResult<Unit>>? = null

            try {
                assertTrue(
                    firstTransactionHasLock.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "The first transaction did not acquire the row lock in time",
                )
                secondTask = workerPool.submit<ExecuteResult<Unit>> {
                    secondExecutor.transaction { tx ->
                        when (val selected = tx.queryBuilder(lockingSelectQuery(table, rowId)).execute()) {
                            is ExecuteResult.Failure -> selected
                            is ExecuteResult.Success -> {
                                secondRows.set(selected.result)
                                ExecuteResult.Success(Unit)
                            }
                        }
                    }
                }
                assertTrue(
                    secondSelectEnteredJdbc.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                    "The second transaction did not reach JDBC executeQuery in time",
                )
                assertFailsWith<TimeoutException>(
                    "The second SELECT FOR UPDATE completed while the first transaction still held the row lock",
                ) {
                    requireNotNull(secondTask).get(LOCK_WAIT_OBSERVATION_MILLIS, TimeUnit.MILLISECONDS)
                }
                assertTrue(
                    firstConnection.get() !== secondConnection.get(),
                    "The two transactions must use independent JDBC connections",
                )

                releaseFirstTransaction.countDown()
                val firstResult = firstTask.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (rollbackFirst) {
                    assertSame(requestedRollback, assertIs<ExecuteResult.Failure>(firstResult).exception)
                } else {
                    assertIs<ExecuteResult.Success<Unit>>(firstResult)
                }
                assertIs<ExecuteResult.Success<Unit>>(
                    requireNotNull(secondTask).get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                )
                val rowsAfterRelease = assertNotNull(secondRows.get())
                assertEquals(listOf(rowId), rowsAfterRelease.map { it["id"] })
            } finally {
                releaseFirstTransaction.countDown()
                runCatching { firstTask.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                secondTask?.let { runCatching { it.get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS) } }
                workerPool.shutdownNow()
                if (!workerPool.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    secondConnection.get()?.let { runCatching { it.close() } }
                    firstConnection.get()?.let { runCatching { it.close() } }
                    workerPool.shutdownNow()
                    workerPool.awaitTermination(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                }
            }
        }

    private fun insertLockTestRow(settings: MySqlSettings, table: String): Long =
        openVerifiedConnection(settings).use { connection ->
            connection.prepareStatement(
                "INSERT INTO $table (marker) VALUES (?)",
                Statement.RETURN_GENERATED_KEYS,
            ).use { statement ->
                statement.setString(1, "for_update_target")
                statement.executeUpdate()
                statement.generatedKeys.use { generatedKeys ->
                    check(generatedKeys.next()) { "The lock-test row did not return its generated id" }
                    generatedKeys.getLong(1)
                }
            }
        }

    private fun lockingSelectQuery(table: String, id: Long) = QueryRenderSelectBuilder()
        .select {
            addColumn {
                column { tableSelector("tx_rows").columnSelector("id") }
                alias("id")
                execute(DataType.LONG)
            }
        }
        .from { table(table).alias("tx_rows") }
        .where {
            conditions {
                addCondition {
                    sideSelector { tableSelector("tx_rows").columnSelector("id") }
                    operationEqual()
                    sideValueParam {
                        paramName("row_id")
                        paramValue(id)
                    }
                }
            }
        }
        .apply { lockForUpdate {} }

    private fun withTestTable(block: (MySqlSettings, String) -> Unit) {
        val settings = settingsOrSkip()
        val previousConfig = DefaultDatabaseConfig.config
        DefaultDatabaseConfig.config = settings.databaseConfig()
        val table = "qb_tx_rows_" + UUID.randomUUID().toString().replace("-", "")
        var tableCreated = false
        var primaryFailure: Throwable? = null

        try {
            openVerifiedConnection(settings).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        "CREATE TABLE " + table +
                            " (id BIGINT NOT NULL AUTO_INCREMENT, marker VARCHAR(100) NOT NULL, " +
                            "PRIMARY KEY (id), UNIQUE KEY uq_marker (marker)) ENGINE=InnoDB",
                    )
                }
            }
            tableCreated = true
            block(settings, table)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            try {
                if (tableCreated) {
                    openVerifiedConnection(settings).use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("DROP TABLE IF EXISTS " + table)
                        }
                    }
                }
            } catch (cleanupFailure: Throwable) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(cleanupFailure)
                } else {
                    throw cleanupFailure
                }
            } finally {
                DefaultDatabaseConfig.config = previousConfig
            }
        }
    }

    private fun settingsOrSkip(): MySqlSettings {
        val settings = readSettings(System.getProperties())
        assumeTrue(
            settings != null,
            "MySQL transaction integration is opt-in; set execute.test.db.transaction.enabled=true and all five execute.test.db connection properties",
        )
        return requireNotNull(settings)
    }

    private fun readSettings(properties: Properties): MySqlSettings? {
        if (properties.getProperty(ENABLED_PROPERTY) != "true") return null

        val requiredProperties = listOf(
            HOST_PROPERTY,
            PORT_PROPERTY,
            NAME_PROPERTY,
            USERNAME_PROPERTY,
            PASSWORD_PROPERTY,
        )
        val missing = requiredProperties.filter { properties.getProperty(it) == null }
        check(missing.isEmpty()) {
            "MySQL transaction integration is enabled, but required properties are missing: " +
                missing.joinToString()
        }

        val host = requireNotNull(properties.getProperty(HOST_PROPERTY))
        require(host.isNotBlank() && host == host.trim() && host.none { it.isWhitespace() || it in "/?#@" }) {
            "Property " + HOST_PROPERTY + " must be a host name or address without URL path/query characters"
        }

        val portText = requireNotNull(properties.getProperty(PORT_PROPERTY))
        val port = portText.toIntOrNull()
        require(port != null && port in 1..65535) {
            "Property " + PORT_PROPERTY + " must be an explicitly configured TCP port from 1 to 65535"
        }

        val schema = requireNotNull(properties.getProperty(NAME_PROPERTY))
        require(
            schema == "kotlin_db_test_connection" ||
                Regex("^qb_tx_test_[A-Za-z0-9_]+$").matches(schema),
        ) {
            "Property " + NAME_PROPERTY +
                " must name a pre-created test schema beginning with qb_tx_test_ or exactly kotlin_db_test_connection"
        }

        val username = requireNotNull(properties.getProperty(USERNAME_PROPERTY))
        require(username.isNotBlank()) {
            "Property " + USERNAME_PROPERTY + " must not be blank"
        }

        return MySqlSettings(
            host = host,
            port = port,
            schema = schema,
            username = username,
            password = requireNotNull(properties.getProperty(PASSWORD_PROPERTY)),
        )
    }

    private fun completeProperties(
        host: String,
        port: String,
        schema: String,
        username: String,
        password: String,
    ) = Properties().apply {
        setProperty(ENABLED_PROPERTY, "true")
        setProperty(HOST_PROPERTY, host)
        setProperty(PORT_PROPERTY, port)
        setProperty(NAME_PROPERTY, schema)
        setProperty(USERNAME_PROPERTY, username)
        setProperty(PASSWORD_PROPERTY, password)
    }

    private fun insertQuery(table: String, marker: String) = QueryRenderInsertBuilder()
        .table { table(table) }
        .addValue {
            column("marker")
            setValue {
                paramName("marker")
                paramValue(marker)
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
        .from { table(table).alias("tx_rows") }

    private fun readMarkers(settings: MySqlSettings, table: String): List<String> =
        openVerifiedConnection(settings).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT marker FROM " + table + " ORDER BY id").use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getString(1))
                    }
                }
            }
        }

    private fun openVerifiedConnection(settings: MySqlSettings): Connection {
        val connection = DriverManager.getConnection(
            settings.jdbcUrl(),
            settings.username,
            settings.password,
        )
        try {
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT DATABASE()").use { result ->
                    check(result.next() && result.getString(1) == settings.schema) {
                        "Connected database did not match the explicitly configured test schema"
                    }
                }
            }
            return connection
        } catch (failure: Throwable) {
            try {
                connection.close()
            } catch (cleanupFailure: Throwable) {
                if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    private data class MySqlSettings(
        val host: String,
        val port: Int,
        val schema: String,
        val username: String,
        val password: String,
    ) {
        fun jdbcUrl(): String =
            "jdbc:mysql://" + host + ":" + port + "/" + schema + "?serverTimezone=UTC"

        fun databaseConfig() = DatabaseConfig(
            dbDomain = "jdbc:mysql://" + host,
            dbPort = port,
            dbName = schema + "?serverTimezone=UTC",
            dbUserName = username,
            dbPassword = password,
        )
    }

    private inner class JdbcResourceProbe(
        private val onExecuteQuery: CountDownLatch? = null,
    ) {
        private var nextConnectionId = 0

        val statementConnectionIds = mutableListOf<Int>()
        var commitCalls = 0
        var rollbackCalls = 0
        var connectionCloseCalls = 0
        var statementCloseCalls = 0
        var selectResultSetCloseCalls = 0
        var generatedKeyResultSetCloseCalls = 0

        fun wrap(connection: Connection): Connection {
            val connectionId = ++nextConnectionId
            return proxy(Connection::class.java) { _, method, arguments ->
                when (method.name) {
                    "commit" -> {
                        commitCalls++
                        invoke(connection, method, arguments)
                    }
                    "rollback" -> {
                        rollbackCalls++
                        invoke(connection, method, arguments)
                    }
                    "close" -> {
                        connectionCloseCalls++
                        invoke(connection, method, arguments)
                    }
                    "prepareStatement" -> {
                        val statement = invoke(connection, method, arguments) as PreparedStatement
                        statementConnectionIds += connectionId
                        wrapStatement(statement)
                    }
                    else -> invoke(connection, method, arguments)
                }
            }
        }

        private fun wrapStatement(statement: PreparedStatement): PreparedStatement =
            proxy(PreparedStatement::class.java) { _, method, arguments ->
                when (method.name) {
                    "close" -> {
                        statementCloseCalls++
                        invoke(statement, method, arguments)
                    }
                    "executeQuery" -> {
                        onExecuteQuery?.countDown()
                        val rows = invoke(statement, method, arguments) as ResultSet
                        wrapResultSet(rows, generatedKeys = false)
                    }
                    "getGeneratedKeys" -> {
                        val rows = invoke(statement, method, arguments) as ResultSet
                        wrapResultSet(rows, generatedKeys = true)
                    }
                    else -> invoke(statement, method, arguments)
                }
            }

        private fun wrapResultSet(rows: ResultSet, generatedKeys: Boolean): ResultSet =
            proxy(ResultSet::class.java) { _, method, arguments ->
                if (method.name == "close") {
                    if (generatedKeys) generatedKeyResultSetCloseCalls++ else selectResultSetCloseCalls++
                }
                invoke(rows, method, arguments)
            }

        private fun invoke(target: Any, method: Method, arguments: Array<out Any?>?): Any? =
            try {
                method.invoke(target, *(arguments ?: emptyArray()))
            } catch (failure: InvocationTargetException) {
                throw failure.targetException
            }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> proxy(type: Class<T>, handler: InvocationHandler): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T

    private companion object {
        const val TEST_TIMEOUT_SECONDS = 12L
        const val LOCK_WAIT_OBSERVATION_MILLIS = 400L
        const val MYSQL_LOCK_WAIT_SECONDS = 8
        const val ENABLED_PROPERTY = "execute.test.db.transaction.enabled"
        const val HOST_PROPERTY = "execute.test.db.host"
        const val PORT_PROPERTY = "execute.test.db.port"
        const val NAME_PROPERTY = "execute.test.db.name"
        const val USERNAME_PROPERTY = "execute.test.db.username"
        const val PASSWORD_PROPERTY = "execute.test.db.password"
    }
}
