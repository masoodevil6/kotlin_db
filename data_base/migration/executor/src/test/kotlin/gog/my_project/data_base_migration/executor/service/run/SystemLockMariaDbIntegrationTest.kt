package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.MARIA_DB
import gog.my_project.data_base_operation.config.data_base.SystemLock
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Types
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

/** Real-server checks for session locks; tests issue SELECT/GET_LOCK/RELEASE_LOCK only. */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("real-mariadb-system-lock")
class SystemLockMariaDbIntegrationTest {
    @Test
    fun `same identity contender waits until first migration lock callback exits`() {
        val settings = settings()
        val firstManager = manager(settings)
        val secondManager = manager(settings)
        val workers = Executors.newFixedThreadPool(2)
        val firstAcquired = CountDownLatch(1)
        val secondAttempting = CountDownLatch(1)
        val secondCallbackEntered = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val events = java.util.concurrent.CopyOnWriteArrayList<String>()
        val firstSessionInfo = AtomicReference<SessionInfo?>()
        val firstId = AtomicLong(-1)
        val firstIdWhileLocked = AtomicLong(-1)
        val firstIdBeforeReturn = AtomicLong(-1)
        val secondId = AtomicLong(-1)
        val secondIdWhileLocked = AtomicLong(-1)

        var firstFuture: java.util.concurrent.Future<ExecuteResult<Unit>>? = null
        var secondFuture: java.util.concurrent.Future<ExecuteResult<Unit>>? = null
        try {
            firstFuture = workers.submit<ExecuteResult<Unit>> {
                firstManager[TestDatabase].withOperationScope { scope ->
                    readSession(scope).also { firstSessionInfo.set(it); firstId.set(it.connectionId) }
                    scope.withSystemLock(SystemLock.MIGRATION, 20) { lockedScope ->
                        firstIdWhileLocked.set(readSession(lockedScope).connectionId)
                        events += "first-callback-entered"
                        firstAcquired.countDown()
                        if (!releaseFirst.await(15, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(TimeoutException("test release latch timed out"))
                        } else {
                            firstIdBeforeReturn.set(readSession(lockedScope).connectionId)
                            events += "first-callback-exiting"
                            ExecuteResult.Success(Unit)
                        }
                    }
                }
            }

            assertTrue(firstAcquired.await(8, TimeUnit.SECONDS), "First connection did not acquire SystemLock.MIGRATION")

            secondFuture = workers.submit<ExecuteResult<Unit>> {
                secondManager[TestDatabase].withOperationScope { scope ->
                    secondId.set(readSession(scope).connectionId)
                    secondAttempting.countDown()
                    scope.withSystemLock(SystemLock.MIGRATION, 20) { lockedScope ->
                        secondIdWhileLocked.set(readSession(lockedScope).connectionId)
                        events += "second-callback-entered"
                        secondCallbackEntered.countDown()
                        ExecuteResult.Success(Unit)
                    }
                }
            }

            assertTrue(secondAttempting.await(8, TimeUnit.SECONDS), "Second connection did not begin its lock call")
            val waitObservation = awaitLockWaitState(settings, secondId.get(), 8_000)
            val serverWaitState = waitObservation.state
            assertTrue(
                serverWaitState?.contains("lock", ignoreCase = true) == true,
                "MariaDB did not report the second session waiting for its advisory lock",
            )
            assertFalse(
                secondCallbackEntered.await(100, TimeUnit.MILLISECONDS),
                "Second callback ran while the first session still held the lock",
            )

            events += "test-releases-first"
            releaseFirst.countDown()

            assertTrue(secondCallbackEntered.await(8, TimeUnit.SECONDS), "Second callback did not run after release")
            assertIs<ExecuteResult.Success<Unit>>(firstFuture.get(10, TimeUnit.SECONDS))
            assertIs<ExecuteResult.Success<Unit>>(secondFuture.get(10, TimeUnit.SECONDS))
            assertTrue(firstId.get() > 0)
            assertTrue(secondId.get() > 0)
            assertNotEquals(firstId.get(), secondId.get(), "Operation scopes must use independent JDBC sessions")
            assertEquals(firstId.get(), firstIdWhileLocked.get(), "First lock callback changed Connection ID")
            assertEquals(firstId.get(), firstIdBeforeReturn.get(), "First lock callback changed Connection ID before return")
            assertEquals(secondId.get(), secondIdWhileLocked.get(), "Second lock callback changed Connection ID")
            assertTrue(events.indexOf("first-callback-entered") < events.indexOf("test-releases-first"))
            assertTrue(events.indexOf("test-releases-first") < events.indexOf("first-callback-exiting"))
            assertTrue(events.indexOf("first-callback-exiting") < events.indexOf("second-callback-entered"))
            println(
                "REAL_LOCK_AUDIT same-identity version=${assertNotNull(firstSessionInfo.get()).version} " +
                    "firstSession=${firstId.get()} secondSession=${secondId.get()} " +
                    "observerSession=${waitObservation.observerSessionId} " +
                    "serverWaitState=$serverWaitState events=${events.joinToString(",")}",
            )
        } finally {
            releaseFirst.countDown()
            finishWorkers(workers, firstFuture, secondFuture)
            firstManager.shutdown()
            secondManager.shutdown()
        }
    }

    @Test
    fun `timed out contender does not run callback and leaves first session lock held`() {
        val settings = settings()
        val firstManager = manager(settings)
        val contenderManager = manager(settings)
        val workers = Executors.newSingleThreadExecutor()
        val firstAcquired = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val firstId = AtomicLong(-1)
        val firstIdBeforeReturn = AtomicLong(-1)
        val firstSessionInfo = AtomicReference<SessionInfo?>()
        val contenderCallbackRan = AtomicBoolean(false)
        val timedOutId = AtomicLong(-1)
        val stillContendedId = AtomicLong(-1)
        var firstFuture: java.util.concurrent.Future<ExecuteResult<Unit>>? = null

        try {
            firstFuture = workers.submit<ExecuteResult<Unit>> {
                firstManager[TestDatabase].withOperationScope { scope ->
                    readSession(scope).also { firstSessionInfo.set(it); firstId.set(it.connectionId) }
                    scope.withSystemLock(SystemLock.MIGRATION, 20) { lockedScope ->
                        firstAcquired.countDown()
                        if (!releaseFirst.await(15, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(TimeoutException("test release latch timed out"))
                        } else {
                            firstIdBeforeReturn.set(readSession(lockedScope).connectionId)
                            ExecuteResult.Success(Unit)
                        }
                    }
                }
            }
            assertTrue(firstAcquired.await(8, TimeUnit.SECONDS))

            val timedOut = contenderManager[TestDatabase].withOperationScope { scope ->
                timedOutId.set(readSession(scope).connectionId)
                scope.withSystemLock(SystemLock.MIGRATION, 1) {
                    contenderCallbackRan.set(true)
                    ExecuteResult.Success(Unit)
                }
            }
            val timeoutFailure = assertIs<ExecuteResult.Failure>(timedOut)
            assertIs<TimeoutException>(timeoutFailure.exception)
            assertFalse(contenderCallbackRan.get(), "Timeout must not invoke the second callback")

            val stillBlocked = contenderManager[TestDatabase].withOperationScope { scope ->
                stillContendedId.set(readSession(scope).connectionId)
                scope.withSystemLock(SystemLock.MIGRATION, 0) {
                    contenderCallbackRan.set(true)
                    ExecuteResult.Success(Unit)
                }
            }
            assertIs<ExecuteResult.Failure>(stillBlocked)
            assertFalse(contenderCallbackRan.get(), "A zero-timeout probe must also fail while the first lock is held")
            assertNotEquals(firstId.get(), timedOutId.get())
            assertNotEquals(firstId.get(), stillContendedId.get())

            releaseFirst.countDown()
            assertIs<ExecuteResult.Success<Unit>>(firstFuture.get(10, TimeUnit.SECONDS))
            assertEquals(firstId.get(), firstIdBeforeReturn.get())
            println(
                "REAL_LOCK_AUDIT timeout version=${assertNotNull(firstSessionInfo.get()).version} " +
                    "ownerSession=${firstId.get()} timeoutSession=${timedOutId.get()} " +
                    "postTimeoutProbeSession=${stillContendedId.get()} callbackRan=${contenderCallbackRan.get()}",
            )
        } finally {
            releaseFirst.countDown()
            finishWorkers(workers, firstFuture, null)
            firstManager.shutdown()
            contenderManager.shutdown()
        }
    }

    @Test
    fun `migration lock is released after a successful callback`() {
        val settings = settings()
        val firstManager = manager(settings)
        val secondManager = manager(settings)
        try {
            val firstId = requireSuccess(
                withMigrationLock(firstManager, 5) { scope ->
                    ExecuteResult.Success(readSession(scope))
                },
            )
            val second = requireSuccess(
                withMigrationLock(secondManager, 0) { scope ->
                    ExecuteResult.Success(readSession(scope))
                },
            )
            assertNotEquals(firstId.connectionId, second.connectionId)
            assertEquals(firstId.version, second.version)
            println("REAL_LOCK_AUDIT successful-release version=${firstId.version} sessions=${firstId.connectionId},${second.connectionId}")
        } finally {
            firstManager.shutdown()
            secondManager.shutdown()
        }
    }

    @Test
    fun `migration lock is released after a failed callback and keeps callback failure`() {
        val settings = settings()
        val firstManager = manager(settings)
        val secondManager = manager(settings)
        val marker = IllegalStateException("callback marker")
        val firstSessionInfo = AtomicReference<SessionInfo?>()
        try {
            val failed = withMigrationLock(firstManager, 5) { scope ->
                firstSessionInfo.set(readSession(scope))
                ExecuteResult.Failure(marker)
            }
            assertSame(marker, assertIs<ExecuteResult.Failure>(failed).exception)

            val secondId = requireSuccess(
                withMigrationLock(secondManager, 0) { scope ->
                    ExecuteResult.Success(readSession(scope).connectionId)
                },
            )
            assertTrue(secondId > 0)
            val failedSession = assertNotNull(firstSessionInfo.get())
            println(
                "REAL_LOCK_AUDIT failed-callback-release version=${failedSession.version} " +
                    "failedSession=${failedSession.connectionId} nextSession=$secondId primaryFailurePreserved=true",
            )
        } finally {
            firstManager.shutdown()
            secondManager.shutdown()
        }
    }

    @Test
    fun `successful callback with lock already released is reported as release failure`() {
        val settings = settings()
        val firstManager = manager(settings)
        val secondManager = manager(settings)
        val firstId = AtomicLong(-1)
        val firstSessionInfo = AtomicReference<SessionInfo?>()
        try {
            val result = withMigrationLock(firstManager, 5) { scope ->
                readSession(scope).also { firstSessionInfo.set(it); firstId.set(it.connectionId) }
                val identity = assertNotNull(DatabaseIdentity.from(settings.databaseConfig(settings.host)))
                val lockName = systemLockName(SystemLock.MIGRATION, identity)
                val releasedCount = requireSuccess(
                    scope.executeSelect(
                        BoundQuery(
                            "SELECT RELEASE_LOCK(?) AS released",
                            listOf(QueryParameterBinding("lockName", lockName, Types.VARCHAR)),
                        ),
                    ) { rows ->
                        check(rows.next()) { "RELEASE_LOCK returned no row" }
                        requireNotNull(rows.getLong("released"))
                    },
                )
                assertEquals(1L, releasedCount, "The callback should release the lock held by its own session")
                ExecuteResult.Success(Unit)
            }
            val releaseFailure = assertIs<ExecuteResult.Failure>(result)
            assertIs<IllegalStateException>(releaseFailure.exception)
            val sessionCheck = checkSessionPresence(settings, firstId.get())
            assertFalse(sessionCheck.present, "Operation must close the Connection after lock release failure")

            val nextId = requireSuccess(
                withMigrationLock(secondManager, 0) { scope ->
                    ExecuteResult.Success(readSession(scope).connectionId)
                },
            )
            assertNotEquals(firstId.get(), nextId)
            println(
                "REAL_LOCK_AUDIT failed-release version=${assertNotNull(firstSessionInfo.get()).version} " +
                    "originalSession=${firstId.get()} observerSession=${sessionCheck.observerSessionId} " +
                    "nextSession=$nextId originalSessionPresent=${sessionCheck.present}",
            )
        } finally {
            firstManager.shutdown()
            secondManager.shutdown()
        }
    }

    @Test
    fun `MariaDB releases advisory lock when its owning session ends without explicit release`() {
        val settings = settings()
        val name = "textqb:stage3:session-end:${UUID.randomUUID().toString().replace("-", "")}"
        val owner = settings.openConnection(settings.host)
        val observer = settings.openConnection(settings.host)
        var ownerId = -1L
        try {
            val version = readServerVersion(owner)
            ownerId = connectionId(owner)
            val observerId = connectionId(observer)
            assertEquals(1L, scalarLong(owner, "SELECT GET_LOCK(?, 0)", name))
            assertEquals(ownerId, scalarLong(observer, "SELECT IS_USED_LOCK(?)", name))

            owner.close()

            val releasedAfterSessionEnd = awaitLockOwner(observer, name, 5_000)
            assertEquals(null, releasedAfterSessionEnd, "Closing the owning session must release its advisory lock")
            println("REAL_LOCK_AUDIT session-end version=$version ownerSession=$ownerId observerSession=$observerId explicitRelease=false released=true")
        } finally {
            try { owner.close() } catch (_: Throwable) { }
            observer.close()
        }
    }

    @Test
    fun `different DatabaseIdentity values hold independent migration locks on the same server`() {
        val settings = settings()
        val firstConfig = settings.databaseConfig(settings.host)
        val secondConfig = settings.databaseConfig(ALTERNATE_LOOPBACK)
        val firstIdentity = assertNotNull(DatabaseIdentity.from(firstConfig))
        val secondIdentity = assertNotNull(DatabaseIdentity.from(secondConfig))
        assertNotEquals(firstIdentity, secondIdentity)

        val firstManager = manager(settings, settings.host)
        val secondManager = manager(settings, ALTERNATE_LOOPBACK)
        val workers = Executors.newFixedThreadPool(2)
        val bothInsideCallbacks = CountDownLatch(2)
        val releaseBoth = CountDownLatch(1)
        val firstSession = AtomicReference<SessionInfo?>()
        val secondSession = AtomicReference<SessionInfo?>()
        var firstFuture: java.util.concurrent.Future<ExecuteResult<Unit>>? = null
        var secondFuture: java.util.concurrent.Future<ExecuteResult<Unit>>? = null

        try {
            firstFuture = workers.submit<ExecuteResult<Unit>> {
                withMigrationLock(firstManager, 8) { scope ->
                    firstSession.set(readSession(scope))
                    bothInsideCallbacks.countDown()
                    if (!releaseBoth.await(12, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(TimeoutException("test release latch timed out"))
                    } else ExecuteResult.Success(Unit)
                }
            }
            secondFuture = workers.submit<ExecuteResult<Unit>> {
                withMigrationLock(secondManager, 8) { scope ->
                    secondSession.set(readSession(scope))
                    bothInsideCallbacks.countDown()
                    if (!releaseBoth.await(12, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(TimeoutException("test release latch timed out"))
                    } else ExecuteResult.Success(Unit)
                }
            }

            val simultaneous = bothInsideCallbacks.await(10, TimeUnit.SECONDS)
            releaseBoth.countDown()
            assertTrue(simultaneous, "Different identities should not contend for the same final lock name")
            assertIs<ExecuteResult.Success<Unit>>(firstFuture.get(10, TimeUnit.SECONDS))
            assertIs<ExecuteResult.Success<Unit>>(secondFuture.get(10, TimeUnit.SECONDS))

            val first = assertNotNull(firstSession.get())
            val second = assertNotNull(secondSession.get())
            assertNotEquals(first.connectionId, second.connectionId)
            assertEquals(first.database, second.database)
            assertEquals(first.version, second.version)
            assertEquals(first.serverId, second.serverId)
            println(
                "REAL_LOCK_AUDIT distinct-identities version=${first.version} " +
                    "identityValuesDiffer=true sameDatabase=true sameServerId=true " +
                    "sessionIds=${first.connectionId},${second.connectionId} callbacksOverlapped=true",
            )
        } finally {
            releaseBoth.countDown()
            finishWorkers(workers, firstFuture, secondFuture)
            firstManager.shutdown()
            secondManager.shutdown()
        }
    }

    private fun settings(): MariaDbSettings {
        assumeTrue(System.getenv(ENABLED) == "true") { "Set $ENABLED=true to opt in to real MariaDB lock tests" }
        val host = System.getenv(HOST).orEmpty()
        val port = System.getenv(PORT)?.toIntOrNull()
        val schema = System.getenv(SCHEMA).orEmpty()
        val username = System.getenv(USERNAME).orEmpty()
        val password = System.getenv(PASSWORD).orEmpty()
        assumeTrue(host in ALLOWED_LOCAL_HOSTS) { "Real SystemLock tests only allow a local MariaDB endpoint" }
        assumeTrue(port in 1..65535 && schema.isNotBlank() && username.isNotBlank()) {
            "MariaDB endpoint, schema, and username environment settings are required"
        }
        return MariaDbSettings(host, requireNotNull(port), schema, username, password)
    }

    private fun manager(settings: MariaDbSettings, host: String = settings.host): DatabaseManager =
        DatabaseManager {
            database(TestDatabase) {
                config = settings.databaseConfig(host)
            }
        }

    private fun <T> withMigrationLock(
        manager: DatabaseManager,
        timeoutSeconds: Int,
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> = manager[TestDatabase].withOperationScope { scope ->
        scope.withSystemLock(SystemLock.MIGRATION, timeoutSeconds, block)
    }

    private fun readSession(scope: OperationExecutionScope): SessionInfo =
        requireSuccess(
            scope.executeSelect(
                BoundQuery(
                    "SELECT VERSION() AS server_version, DATABASE() AS database_name, " +
                        "@@server_id AS server_id, CONNECTION_ID() AS connection_id",
                    emptyList(),
                ),
            ) { rows ->
                check(rows.next()) { "Session information query returned no row" }
                SessionInfo(
                    version = requireNotNull(rows.getString("server_version")),
                    database = requireNotNull(rows.getString("database_name")),
                    serverId = requireNotNull(rows.getLong("server_id")),
                    connectionId = requireNotNull(rows.getLong("connection_id")),
                )
            },
        )

    private fun awaitLockWaitState(
        settings: MariaDbSettings,
        sessionId: Long,
        timeoutMillis: Long,
    ): LockWaitObservation {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        settings.openConnection(settings.host).use { observer ->
            val observerSessionId = connectionId(observer)
            observer.prepareStatement("SELECT STATE FROM information_schema.PROCESSLIST WHERE ID = ?").use { statement ->
                while (System.nanoTime() < deadline) {
                    statement.setLong(1, sessionId)
                    statement.executeQuery().use { rows ->
                        if (rows.next()) {
                            val state = rows.getString("STATE")
                            if (state?.contains("lock", ignoreCase = true) == true) {
                                return LockWaitObservation(state, observerSessionId)
                            }
                        }
                    }
                    Thread.sleep(25)
                }
            }
            return LockWaitObservation(null, observerSessionId)
        }
    }

    private fun awaitLockOwner(connection: Connection, name: String, timeoutMillis: Long): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        connection.prepareStatement("SELECT IS_USED_LOCK(?) AS owner_id").use { statement ->
            while (System.nanoTime() < deadline) {
                statement.setString(1, name)
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "IS_USED_LOCK returned no row" }
                    val owner = rows.getLong("owner_id").let { if (rows.wasNull()) null else it }
                    if (owner == null) return null
                }
                Thread.sleep(25)
            }
        }
        return scalarLong(connection, "SELECT IS_USED_LOCK(?)", name)
    }

    private fun checkSessionPresence(settings: MariaDbSettings, sessionId: Long): SessionPresence =
        settings.openConnection(settings.host).use { observer ->
            val observerSessionId = connectionId(observer)
            val present = observer.prepareStatement(
                "SELECT 1 FROM information_schema.PROCESSLIST WHERE ID = ?",
            ).use { statement ->
                statement.setLong(1, sessionId)
                statement.executeQuery().use { rows -> rows.next() }
            }
            SessionPresence(present, observerSessionId)
        }

    private fun connectionId(connection: Connection): Long =
        requireNotNull(scalarLong(connection, "SELECT CONNECTION_ID()"))

    private fun readServerVersion(connection: Connection): String =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT VERSION()").use { rows ->
                check(rows.next()) { "VERSION query returned no row" }
                requireNotNull(rows.getString(1))
            }
        }

    private fun scalarLong(connection: Connection, sql: String, parameter: String? = null): Long? =
        connection.prepareStatement(sql).use { statement ->
            if (parameter != null) statement.setString(1, parameter)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "Scalar query returned no row" }
                rows.getLong(1).let { if (rows.wasNull()) null else it }
            }
        }

    private fun <T> requireSuccess(result: ExecuteResult<T>): T = when (result) {
        is ExecuteResult.Success -> requireNotNull(result.result) { "Expected a non-null successful result" }
        is ExecuteResult.Failure -> throw AssertionError("Expected Operation success, received ${result.exception::class.simpleName}")
    }

    /** Mirrors OperationExecutionScope's private v1 lock-name encoding for the release-failure probe. */
    private fun systemLockName(systemLock: SystemLock, identity: DatabaseIdentity): String {
        val encoded = ByteArrayOutputStream().also { buffer ->
            DataOutputStream(buffer).use { output ->
                writeLockField(output, "textqb.system-lock")
                output.writeInt(1)
                writeLockField(output, systemLock.id)
                writeLockField(output, identity.value)
            }
        }.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
        val hex = digest.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        return "textqb:syslock:v1:" + hex.take(46)
    }

    private fun writeLockField(output: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        output.writeInt(bytes.size)
        output.write(bytes)
    }

    private fun finishWorkers(
        workers: java.util.concurrent.ExecutorService,
        vararg futures: java.util.concurrent.Future<ExecuteResult<Unit>>?,
    ) {
        futures.filterNotNull().forEach { future ->
            try { future.get(10, TimeUnit.SECONDS) } catch (_: Throwable) { }
        }
        workers.shutdownNow()
        workers.awaitTermination(5, TimeUnit.SECONDS)
    }

    private data class SessionInfo(
        val version: String,
        val database: String,
        val serverId: Long,
        val connectionId: Long,
    )

    private data class LockWaitObservation(
        val state: String?,
        val observerSessionId: Long,
    )

    private data class SessionPresence(
        val present: Boolean,
        val observerSessionId: Long,
    )

    private data class MariaDbSettings(
        val host: String,
        val port: Int,
        val schema: String,
        val username: String,
        val password: String,
    ) {
        fun databaseConfig(host: String) = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://$host",
                dbPort = port,
                dbName = schema,
                dbUserName = username,
                dbPassword = password,
            ),
            platform = DatabasePlatformConfig(DialectQuery.MARIA_DB, MARIA_DB.Version(10, 4, 28)),
        )

        fun openConnection(host: String): Connection = DriverManager.getConnection(
            "jdbc:mysql://$host:$port/$schema",
            username,
            password,
        )
    }

    private object TestDatabase : DatabaseKey

    private companion object {
        const val ENABLED = "KOTLIN_DB_SYSTEM_LOCK_AUDIT_ENABLED"
        const val HOST = "KOTLIN_DB_EXECUTE_TEST_HOST"
        const val PORT = "KOTLIN_DB_EXECUTE_TEST_PORT"
        const val SCHEMA = "KOTLIN_DB_EXECUTE_TEST_DATABASE"
        const val USERNAME = "KOTLIN_DB_EXECUTE_TEST_USERNAME"
        const val PASSWORD = "KOTLIN_DB_EXECUTE_TEST_PASSWORD"
        const val ALTERNATE_LOOPBACK = "127.0.0.2"
        val ALLOWED_LOCAL_HOSTS = setOf("127.0.0.1", "localhost")
    }
}
