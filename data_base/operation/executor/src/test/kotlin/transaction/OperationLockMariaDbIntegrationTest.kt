package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.manager.DatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseProduct
import gog.my_project.data_base_operation.config.data_base.DatabaseServerInfo
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import java.sql.DriverManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Tag("mysql-integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("operation-migration-lock-mariadb-integration")
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class OperationLockMariaDbIntegrationTest {
    private lateinit var settings: MariaDbIntegrationSettings
    private lateinit var server: InspectedMariaDb

    @BeforeAll
    fun inspectConfiguredServer() {
        settings = MariaDbIntegrationSettings.fromEnvironment()
        server = settings.inspectMariaDb()
        println(
            "Operation lock MariaDB integration target: ${server.product} ${server.version}; " +
                "host=${settings.host}; port=${settings.port}; schema=${settings.schema}; " +
                "driver=${server.driverName} ${server.driverVersion}",
        )
    }

    @Test
    fun acquisition_releases_for_a_second_operation_and_closes_the_owner_connection(testInfo: TestInfo) {
        val identity = settings.identity(settings.domain)
        val operationConnectionIds = ConcurrentHashMap.newKeySet<Long>()
        reportStart(testInfo, listOf(identity))

        val bracketExited = CountDownLatch(1)
        val allowOwnerClose = CountDownLatch(1)
        val ownerInfo = AtomicReference<ConnectionInfo?>()
        val workers = Executors.newSingleThreadExecutor()

        try {
            val owner = workers.submit<ExecuteResult<ConnectionInfo>> {
                runOperation(settings.domain) { scope ->
                    val lockResult = scope.withOperationLock(
                        MIGRATION_RESOURCE,
                        LockWaitPolicy.NoWait,
                    ) { callbackContext ->
                        assertSame(scope, callbackContext)
                        val info = readConnectionInfo(scope)
                        operationConnectionIds += info.connectionId
                        ownerInfo.set(info)
                        ExecuteResult.Success(info)
                    }
                    val callbackInfo = acquiredValue(lockResult)
                    val afterBracket = readConnectionInfo(scope)
                    assertEquals(callbackInfo, afterBracket)
                    bracketExited.countDown()
                    if (!allowOwnerClose.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(TimeoutException("Test did not release the owner Operation scope"))
                    } else {
                        ExecuteResult.Success(afterBracket)
                    }
                }
            }

            assertTrue(bracketExited.await(WAIT_SECONDS, TimeUnit.SECONDS), "Operation lock bracket did not finish")
            val first = assertNotNull(ownerInfo.get())
            assertTrue(settings.sessionExists(first.connectionId), "MariaDB did not expose the active owner session")

            val second = acquireInNewOperation(settings.domain, operationConnectionIds)
            assertNotEquals(first.connectionId, second.connectionId)
            assertEquals(identity, settings.identity(settings.domain))
            assertEquals(first.serverId, second.serverId)
            assertTrue(
                settings.sessionExists(first.connectionId),
                "The original Connection must remain open while the second Operation proves Release succeeded",
            )

            allowOwnerClose.countDown()
            assertEquals(first, success(await(owner)))
            assertFalse(settings.sessionExists(first.connectionId), "Operation did not close the owner Connection")
            assertTrue(operationConnectionIds.size >= 2)
            reportPass(testInfo, listOf(identity), operationConnectionIds)
        } finally {
            allowOwnerClose.countDown()
            shutdown(workers)
        }
    }

    @Test
    fun same_identity_contender_times_out_then_acquires_after_owner_release(testInfo: TestInfo) {
        val identity = settings.identity(settings.domain)
        val operationConnectionIds = ConcurrentHashMap.newKeySet<Long>()
        reportStart(testInfo, listOf(identity))

        val ownerAcquired = CountDownLatch(1)
        val allowOwnerRelease = CountDownLatch(1)
        val ownerInfo = AtomicReference<ConnectionInfo?>()
        val workers = Executors.newFixedThreadPool(2)

        try {
            val owner = holdOperationLock(
                workers,
                settings.domain,
                ownerAcquired,
                allowOwnerRelease,
                ownerInfo,
                operationConnectionIds,
            )
            assertTrue(ownerAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS), "Owner did not acquire the Operation lock")

            val ownerConnection = assertNotNull(ownerInfo.get())
            assertTrue(settings.sessionExists(ownerConnection.connectionId), "Owner session was not visible on MariaDB")

            val timedOut = CountDownLatch(1)
            val retryAfterOwnerRelease = CountDownLatch(1)
            val contenderInfo = AtomicReference<ConnectionInfo?>()
            val contenderCallbackRan = java.util.concurrent.atomic.AtomicBoolean(false)
            val contender = workers.submit<ExecuteResult<OperationLockResult<ConnectionInfo>>> {
                runOperation(settings.domain) { scope ->
                    val beforeWait = readConnectionInfo(scope)
                    operationConnectionIds += beforeWait.connectionId
                    contenderInfo.set(beforeWait)

                    val timeoutResult = scope.withOperationLock(
                        MIGRATION_RESOURCE,
                        LockWaitPolicy.Timeout.of(1.seconds),
                    ) { _ ->
                        contenderCallbackRan.set(true)
                        ExecuteResult.Success(Unit)
                    }
                    assertEquals(OperationLockResult.TimedOut, success(timeoutResult))
                    assertFalse(contenderCallbackRan.get(), "Timed-out Operation callback must not run")

                    val afterTimeout = readConnectionInfo(scope)
                    assertEquals(beforeWait.connectionId, afterTimeout.connectionId)
                    assertEquals(beforeWait.database, afterTimeout.database)
                    timedOut.countDown()

                    if (!retryAfterOwnerRelease.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(TimeoutException("Owner did not finish releasing the lock"))
                    } else {
                        scope.withOperationLock(MIGRATION_RESOURCE, LockWaitPolicy.NoWait) { callbackContext ->
                            assertSame(scope, callbackContext)
                            contenderCallbackRan.set(true)
                            ExecuteResult.Success(readConnectionInfo(scope))
                        }
                    }
                }
            }

            assertTrue(timedOut.await(WAIT_SECONDS, TimeUnit.SECONDS), "Contender did not reach its timeout result")
            val contenderConnection = assertNotNull(contenderInfo.get())
            assertTrue(contenderConnection.connectionId != ownerConnection.connectionId)
            assertTrue(settings.sessionExists(contenderConnection.connectionId), "Contender Scope was not valid after timeout")

            allowOwnerRelease.countDown()
            assertEquals(ownerConnection, acquiredValue(await(owner)))
            retryAfterOwnerRelease.countDown()

            val reacquired = acquiredValue(await(contender))
            assertEquals(contenderConnection.connectionId, reacquired.connectionId)
            assertTrue(contenderCallbackRan.get(), "Callback should run for the successful retry")
            assertTrue(operationConnectionIds.size >= 2)
            reportPass(testInfo, listOf(identity), operationConnectionIds)
        } finally {
            allowOwnerRelease.countDown()
            shutdown(workers)
        }
    }

    @Test
    fun different_database_identities_do_not_contend_on_the_same_mariadb_server(testInfo: TestInfo) {
        val firstDomain = settings.domain
        val secondDomain = settings.alternateLoopbackDomain
        val firstIdentity = settings.identity(firstDomain)
        val secondIdentity = settings.identity(secondDomain)
        assertNotEquals(firstIdentity, secondIdentity)
        val operationConnectionIds = ConcurrentHashMap.newKeySet<Long>()
        reportStart(testInfo, listOf(firstIdentity, secondIdentity))

        val bothAcquired = CountDownLatch(2)
        val allowRelease = CountDownLatch(1)
        val firstInfo = AtomicReference<ConnectionInfo?>()
        val secondInfo = AtomicReference<ConnectionInfo?>()
        val workers = Executors.newFixedThreadPool(2)

        try {
            val first = holdOperationLock(
                workers,
                firstDomain,
                bothAcquired,
                allowRelease,
                firstInfo,
                operationConnectionIds,
            )
            val second = holdOperationLock(
                workers,
                secondDomain,
                bothAcquired,
                allowRelease,
                secondInfo,
                operationConnectionIds,
            )

            assertTrue(
                bothAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS),
                "Distinct DatabaseIdentity values should acquire the same logical Resource concurrently",
            )
            val firstConnection = assertNotNull(firstInfo.get())
            val secondConnection = assertNotNull(secondInfo.get())
            assertTrue(settings.sessionExists(firstConnection.connectionId), "First owner session was not visible")
            assertTrue(settings.sessionExists(secondConnection.connectionId), "Second owner session was not visible")
            assertEquals(firstConnection.database, secondConnection.database)
            assertEquals(firstConnection.serverId, secondConnection.serverId)
            assertEquals(firstConnection.version, secondConnection.version)
            assertNotEquals(firstConnection.connectionId, secondConnection.connectionId)

            allowRelease.countDown()
            assertEquals(firstConnection, acquiredValue(await(first)))
            assertEquals(secondConnection, acquiredValue(await(second)))
            assertFalse(settings.sessionExists(firstConnection.connectionId))
            assertFalse(settings.sessionExists(secondConnection.connectionId))
            assertTrue(operationConnectionIds.size >= 2)
            reportPass(testInfo, listOf(firstIdentity, secondIdentity), operationConnectionIds)
        } finally {
            allowRelease.countDown()
            shutdown(workers)
        }
    }

    @Test
    fun returned_failure_and_callback_exception_release_and_close_the_connection(testInfo: TestInfo) {
        val identity = settings.identity(settings.domain)
        val operationConnectionIds = ConcurrentHashMap.newKeySet<Long>()
        reportStart(testInfo, listOf(identity))

        val workers = Executors.newFixedThreadPool(2)
        try {
            for (callbackThrows in listOf(false, true)) {
                val marker: Throwable = if (callbackThrows) {
                    IllegalArgumentException("operation-lock callback exception")
                } else {
                    IllegalStateException("operation-lock callback failure")
                }
                val bracketExited = CountDownLatch(1)
                val allowScopeClose = CountDownLatch(1)
                val ownerConnectionId = AtomicReference<Long?>()
                val owner = holdFailedCallback(
                    workers,
                    settings.domain,
                    marker,
                    callbackThrows,
                    bracketExited,
                    allowScopeClose,
                    ownerConnectionId,
                    operationConnectionIds,
                )

                assertTrue(bracketExited.await(WAIT_SECONDS, TimeUnit.SECONDS), "Failure callback did not exit its lock bracket")
                val ownerId = assertNotNull(ownerConnectionId.get())
                assertTrue(settings.sessionExists(ownerId), "Failure owner Connection should remain open after its bracket")

                try {
                    val reacquired = acquireInNewOperation(settings.domain, operationConnectionIds)
                    assertNotEquals(ownerId, reacquired.connectionId)
                    assertTrue(
                        settings.sessionExists(ownerId),
                        "A second Operation should acquire after Release while the failed owner Connection remains open",
                    )
                } finally {
                    allowScopeClose.countDown()
                }

                assertSame(marker, assertIs<ExecuteResult.Failure>(await(owner)).exception)
                assertFalse(settings.sessionExists(ownerId), "Operation did not close the failed owner Connection")
            }
        } finally {
            shutdown(workers)
        }

        reportPass(testInfo, listOf(identity), operationConnectionIds)
    }

    @Test
    fun callback_error_is_rethrown_after_release_and_connection_cleanup(testInfo: TestInfo) {
        val identity = settings.identity(settings.domain)
        val operationConnectionIds = ConcurrentHashMap.newKeySet<Long>()
        reportStart(testInfo, listOf(identity))

        val callbackError = AssertionError("controlled Operation lock callback Error")
        val ownerConnectionId = AtomicReference<Long?>()
        val errorBracketExited = CountDownLatch(1)
        val allowScopeClose = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val owner = workers.submit<ExecuteResult<OperationLockResult<ConnectionInfo>>> {
                runOperation(settings.domain) { scope ->
                    try {
                        scope.withOperationLock<ConnectionInfo>(MIGRATION_RESOURCE, LockWaitPolicy.NoWait) { callbackContext ->
                            assertSame(scope, callbackContext)
                            val info = readConnectionInfo(scope)
                            operationConnectionIds += info.connectionId
                            ownerConnectionId.set(info.connectionId)
                            throw callbackError
                        }
                    } catch (caught: AssertionError) {
                        assertSame(callbackError, caught)
                        errorBracketExited.countDown()
                        if (!allowScopeClose.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            throw TimeoutException("Test did not release the fatal Operation scope")
                        }
                        throw caught
                    }
                }
            }

            assertTrue(errorBracketExited.await(WAIT_SECONDS, TimeUnit.SECONDS), "Error callback did not exit its lock bracket")
            val ownerId = assertNotNull(ownerConnectionId.get())
            assertTrue(settings.sessionExists(ownerId), "Error owner Connection should remain open after lock Release")
            val reacquired = acquireInNewOperation(settings.domain, operationConnectionIds)
            assertNotEquals(ownerId, reacquired.connectionId)
            assertTrue(
                settings.sessionExists(ownerId),
                "A second Operation should acquire after Error Release while the owner Connection remains open",
            )

            allowScopeClose.countDown()
            val thrown = assertFailsWith<ExecutionException> { await(owner) }
            assertSame(callbackError, thrown.cause)
            assertFalse(settings.sessionExists(ownerId), "Operation did not close the Error owner Connection")
            reportPass(testInfo, listOf(identity), operationConnectionIds)
        } finally {
            allowScopeClose.countDown()
            shutdown(workers)
        }
    }

    private fun holdOperationLock(
        workers: ExecutorService,
        domain: String,
        acquired: CountDownLatch,
        allowRelease: CountDownLatch,
        capturedInfo: AtomicReference<ConnectionInfo?>,
        operationConnectionIds: MutableSet<Long>,
    ): Future<ExecuteResult<OperationLockResult<ConnectionInfo>>> =
        workers.submit<ExecuteResult<OperationLockResult<ConnectionInfo>>> {
        runOperation(domain) { scope ->
            assertEquals(settings.identity(domain), scope.databaseIdentity)
            scope.withOperationLock(MIGRATION_RESOURCE, LockWaitPolicy.NoWait) { callbackContext ->
                assertSame(scope, callbackContext)
                val info = readConnectionInfo(scope)
                operationConnectionIds += info.connectionId
                capturedInfo.set(info)
                acquired.countDown()
                if (!allowRelease.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    ExecuteResult.Failure(TimeoutException("Test did not release the Operation lock owner"))
                } else {
                    ExecuteResult.Success(info)
                }
            }
        }
    }

    private fun holdFailedCallback(
        workers: ExecutorService,
        domain: String,
        marker: Throwable,
        callbackThrows: Boolean,
        bracketExited: CountDownLatch,
        allowScopeClose: CountDownLatch,
        ownerConnectionId: AtomicReference<Long?>,
        operationConnectionIds: MutableSet<Long>,
    ): Future<ExecuteResult<OperationLockResult<ConnectionInfo>>> =
        workers.submit<ExecuteResult<OperationLockResult<ConnectionInfo>>> {
            runOperation(domain) { scope ->
                val result = scope.withOperationLock<ConnectionInfo>(MIGRATION_RESOURCE, LockWaitPolicy.NoWait) { callbackContext ->
                    assertSame(scope, callbackContext)
                    val info = readConnectionInfo(scope)
                    operationConnectionIds += info.connectionId
                    ownerConnectionId.set(info.connectionId)
                    if (callbackThrows) throw marker
                    ExecuteResult.Failure(marker)
                }
                bracketExited.countDown()
                if (!allowScopeClose.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    ExecuteResult.Failure(TimeoutException("Test did not release the failed Operation scope"))
                } else {
                    result
                }
            }
        }

    private fun acquireInNewOperation(
        domain: String,
        operationConnectionIds: MutableSet<Long>,
    ): ConnectionInfo = acquiredValue(
        runOperation(domain) { scope ->
            assertEquals(settings.identity(domain), scope.databaseIdentity)
            scope.withOperationLock(MIGRATION_RESOURCE, LockWaitPolicy.NoWait) { callbackContext ->
                assertSame(scope, callbackContext)
                ExecuteResult.Success(
                    readConnectionInfo(scope).also { operationConnectionIds += it.connectionId },
                )
            }
        },
    )

    private fun <T> runOperation(
        domain: String,
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val config = settings.databaseConfig(domain)
        val identity = requireNotNull(DatabaseIdentity.from(config)) {
            "Integration database settings did not produce a canonical DatabaseIdentity"
        }
        val databaseContext = DatabaseContext(
            name = "operation-migration-lock-mariadb-integration",
            config = config,
            connectionProvider = DatabaseConnection(config.connection),
            databaseIdentity = identity,
        )
        return try {
            OperationExecutionExecutor(databaseContext).connection(block)
        } finally {
            databaseContext.close()
        }
    }

    private fun readConnectionInfo(scope: OperationExecutionScope): ConnectionInfo = success(
        scope.executeSelect(
            BoundQuery(
                "SELECT CONNECTION_ID() AS connection_id, DATABASE() AS database_name, " +
                    "@@server_id AS server_id, VERSION() AS server_version",
                emptyList(),
            ),
        ) { rows ->
            check(rows.next()) { "Connection metadata query returned no row" }
            ConnectionInfo(
                connectionId = requireNotNull(rows.getLong("connection_id")),
                database = requireNotNull(rows.getString("database_name")),
                serverId = requireNotNull(rows.getLong("server_id")),
                version = requireNotNull(rows.getString("server_version")),
            )
        },
    )

    private fun <T : Any> acquiredValue(
        result: ExecuteResult<OperationLockResult<T>>,
    ): T {
        val lockResult = success(result)
        return requireNotNull(assertIs<OperationLockResult.Acquired<T>>(lockResult).value)
    }

    private fun <T> await(future: Future<T>): T = future.get(WAIT_SECONDS, TimeUnit.SECONDS)

    private fun <T> success(result: ExecuteResult<T>): T = when (result) {
        is ExecuteResult.Success -> requireNotNull(result.result) { "Expected a non-null Operation result" }
        is ExecuteResult.Failure -> throw AssertionError("Expected Operation success, received ${result.exception}")
    }

    private fun reportStart(testInfo: TestInfo, identities: List<DatabaseIdentity>) {
        println(
            "REAL_OPERATION_LOCK START test=${testInfo.displayName}; server=${server.product} ${server.version}; " +
                "driver=${server.driverName} ${server.driverVersion}; " +
                "databaseIdentities=${identities.joinToString { it.value }}",
        )
    }

    private fun reportPass(
        testInfo: TestInfo,
        identities: List<DatabaseIdentity>,
        operationConnectionIds: Set<Long>,
    ) {
        println(
            "REAL_OPERATION_LOCK PASS test=${testInfo.displayName}; server=${server.product} ${server.version}; " +
                "driver=${server.driverName} ${server.driverVersion}; " +
                "databaseIdentities=${identities.joinToString { it.value }}; " +
                "independentOperationConnections=${operationConnectionIds.size}; " +
                "connectionIds=${operationConnectionIds.sorted().joinToString()}",
        )
    }

    private fun shutdown(workers: ExecutorService) {
        workers.shutdownNow()
        assertTrue(workers.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS), "Integration workers did not stop")
    }

    private data class ConnectionInfo(
        val connectionId: Long,
        val database: String,
        val serverId: Long,
        val version: String,
    )

    private data class InspectedMariaDb(
        val product: DatabaseProduct,
        val version: String,
        val serverInfo: DatabaseServerInfo,
        val driverName: String,
        val driverVersion: String,
    )

    private data class MariaDbIntegrationSettings(
        val host: String,
        val port: Int,
        val schema: String,
        val username: String,
        val password: String,
    ) {
        val domain: String get() = "jdbc:mysql://$host"
        val alternateLoopbackDomain: String
            get() = "jdbc:mysql://${if (host == "localhost") "127.0.0.1" else "localhost"}"

        fun databaseConfig(domain: String) = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = domain,
                dbPort = port,
                dbName = schema,
                dbUserName = username,
                dbPassword = password,
            ),
            platform = DatabasePlatformConfig(
                dialect = DatabaseDialect.MARIA_DB,
                targetDatabaseVersion = requireNotNull(serverVersion).let(DatabaseServerInfo::parse).version,
            ),
        )

        private var serverVersion: String? = null

        fun identity(domain: String): DatabaseIdentity = requireNotNull(DatabaseIdentity.from(databaseConfig(domain))) {
            "MariaDB integration endpoint did not produce a canonical DatabaseIdentity"
        }

        fun inspectMariaDb(): InspectedMariaDb = openConnection(domain).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT VERSION() AS server_version, @@version_comment AS product_comment, " +
                        "DATABASE() AS database_name, CURRENT_USER() AS connection_account",
                ).use { rows ->
                    check(rows.next()) { "MariaDB did not return its server metadata" }
                    val version = requireNotNull(rows.getString("server_version"))
                    val comment = requireNotNull(rows.getString("product_comment"))
                    val selectedSchema = requireNotNull(rows.getString("database_name"))
                    val connectionAccount = requireNotNull(rows.getString("connection_account"))
                        .substringBefore('@')
                    check(!connectionAccount.equals("root", ignoreCase = true)) {
                        "Operation lock integration requires a non-root MariaDB account."
                    }
                    check(version.contains("MariaDB", ignoreCase = true) &&
                        comment.contains("MariaDB", ignoreCase = true)
                    ) {
                        "Operation lock integration requires a real MariaDB server; received '$version' / '$comment'"
                    }
                    check(selectedSchema == schema) {
                        "Connected schema '$selectedSchema' did not match configured integration schema"
                    }
                    check(!rows.next()) { "MariaDB returned an unexpected extra metadata row" }
                    serverVersion = version
                    val serverInfo = DatabaseServerInfo.parse(version)
                    check(serverInfo.product == DatabaseProduct.MARIA_DB) {
                        "Parsed server metadata did not identify MariaDB"
                    }
                    InspectedMariaDb(
                        product = serverInfo.product,
                        version = version,
                        serverInfo = serverInfo,
                        driverName = connection.metaData.driverName,
                        driverVersion = connection.metaData.driverVersion,
                    )
                }
            }
        }

        fun sessionExists(connectionId: Long): Boolean = openConnection(domain).use { connection ->
            connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE ID = ?",
            ).use { statement ->
                statement.setLong(1, connectionId)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "MariaDB PROCESSLIST query returned no row" }
                    rows.getLong(1) > 0
                }
            }
        }

        private fun openConnection(domain: String) = DriverManager.getConnection(
            "$domain:$port/$schema?serverTimezone=UTC",
            username,
            password,
        )

        companion object {
            fun fromEnvironment(): MariaDbIntegrationSettings {
                val configuredDomain = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_DOMAIN")) {
                    "KOTLIN_DB_OPERATION_IT_DOMAIN must be provided by the local .env or process environment."
                }
                val host = Regex("^jdbc:mysql://(localhost|127\\.0\\.0\\.1)$")
                    .matchEntire(configuredDomain)?.groupValues?.get(1)
                    ?: error("KOTLIN_DB_OPERATION_IT_DOMAIN must point to localhost or 127.0.0.1.")
                val port = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_PORT")?.toIntOrNull()) {
                    "KOTLIN_DB_OPERATION_IT_PORT must be an integer."
                }
                require(port in 1..65535) { "KOTLIN_DB_OPERATION_IT_PORT must be between 1 and 65535." }
                val schema = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_SCHEMA")) {
                    "KOTLIN_DB_OPERATION_IT_SCHEMA must be provided by the local .env or process environment."
                }
                require(schema.matches(DISPOSABLE_SCHEMA_PATTERN)) {
                    "KOTLIN_DB_OPERATION_IT_SCHEMA must use the dedicated Operation integration prefix."
                }
                require(schema !in FORBIDDEN_SCHEMAS) {
                    "Operation integration cannot use a sample, default, or system schema."
                }
                val allowlist = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_ALLOWED_SCHEMAS")) {
                    "KOTLIN_DB_OPERATION_IT_ALLOWED_SCHEMAS must explicitly allow the disposable schema."
                }.split(',').map { it.trim() }
                require(allowlist.isNotEmpty() && allowlist.none { it.isEmpty() }) {
                    "KOTLIN_DB_OPERATION_IT_ALLOWED_SCHEMAS must be a non-empty comma-separated list."
                }
                require(allowlist.distinct().size == allowlist.size) {
                    "KOTLIN_DB_OPERATION_IT_ALLOWED_SCHEMAS must not contain duplicate schema names."
                }
                require(allowlist.all { it.matches(DISPOSABLE_SCHEMA_PATTERN) && it !in FORBIDDEN_SCHEMAS }) {
                    "Every allowlisted schema must use the dedicated disposable Operation integration prefix."
                }
                require(schema in allowlist) {
                    "KOTLIN_DB_OPERATION_IT_SCHEMA must also appear in KOTLIN_DB_OPERATION_IT_ALLOWED_SCHEMAS."
                }
                val username = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_USER")) {
                    "KOTLIN_DB_OPERATION_IT_USER must be provided by the local .env or process environment."
                }
                require(username.isNotBlank() && username == username.trim() &&
                    !username.equals("root", ignoreCase = true)
                ) {
                    "Operation lock integration requires a non-root database account."
                }
                val password = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_PASSWORD")) {
                    "KOTLIN_DB_OPERATION_IT_PASSWORD must be provided by the local .env or process environment."
                }
                require(password.isNotEmpty()) { "KOTLIN_DB_OPERATION_IT_PASSWORD must not be empty." }
                return MariaDbIntegrationSettings(host, port, schema, username, password)
            }
        }
    }

    private companion object {
        val MIGRATION_RESOURCE = LockResource("textqb:migration", "system")
        const val WAIT_SECONDS = 20L
        val DISPOSABLE_SCHEMA_PATTERN = Regex("^kotlin_db_operation_it_[a-z0-9][a-z0-9_]{0,40}$")
        val FORBIDDEN_SCHEMAS = setOf(
            "main_example",
            "analytics_example",
            "test",
            "mysql",
            "information_schema",
            "performance_schema",
            "sys",
        )
    }
}
