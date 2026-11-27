package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseProduct
import gog.my_project.data_base_operation.config.data_base.DatabaseServerInfo
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import gog.my_project.data_base_session.builder.advisory_lock.releaseSessionAdvisoryLock
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock

@Tag("mysql-integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("session-advisory-lock-mysql-integration")
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class SessionOperationMySqlIntegrationTest {
    private lateinit var settings: MySqlIntegrationSettings
    private lateinit var server: InspectedDatabaseServer

    @BeforeAll
    fun inspectConfiguredServer() {
        settings = MySqlIntegrationSettings.fromEnvironment()
        server = settings.inspectServer()
        println(
            "Session advisory-lock integration target: " +
                "${server.info.product} ${server.info.version.major}.${server.info.version.minor}." +
                "${server.info.version.patch}; host=${settings.host}; port=${settings.port}; " +
                "schema=${settings.schema}; driver=${server.driverName} ${server.driverVersion}",
        )
    }

    @Test
    fun acquire_and_release_return_real_server_results() {
        val resource = uniqueResource("acquire-release")
        val result = withOperationConnection { scope ->
            val acquired = operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource)))
            val released = operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
            ExecuteResult.Success(acquired to released)
        }

        val outcome = successValue(result)
        assertEquals(AcquireSessionLockResult.Acquired, outcome.first)
        assertEquals(ReleaseSessionLockResult.Released, outcome.second)
    }

    @Test
    fun release_without_an_existing_lock_returns_not_owned() {
        val resource = uniqueResource("not-owned")
        val result = withOperationConnection { scope ->
            ExecuteResult.Success(operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource))))
        }

        assertEquals(ReleaseSessionLockResult.NotOwned, successValue(result))
    }

    @Test
    fun timeout_on_a_second_connection_ends_after_the_owner_releases() {
        val resource = uniqueResource("timeout")
        val ownerAcquired = CountDownLatch(1)
        val allowOwnerRelease = CountDownLatch(1)
        val ownerReleased = CountDownLatch(1)
        val contenderTimedOut = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val owner = pool.submit<ExecuteResult<ReleaseSessionLockResult>> {
                try {
                    withOperationConnection { scope ->
                        val acquired = operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource)))
                        assertEquals(AcquireSessionLockResult.Acquired, acquired)
                        ownerAcquired.countDown()
                        if (!allowOwnerRelease.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Owner release was not requested in time."))
                        } else {
                            val released = operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                            assertEquals(ReleaseSessionLockResult.Released, released)
                            ownerReleased.countDown()
                            ExecuteResult.Success(released)
                        }
                    }
                } finally {
                    ownerReleased.countDown()
                }
            }

            assertTrue(ownerAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS), "Owner did not acquire the lock")

            val contender = pool.submit<ExecuteResult<ContenderOutcome>> {
                withOperationConnection { scope ->
                    val timedOut = operationValue(
                        scope.acquireSessionAdvisoryLock(acquireAst(resource, timeoutSeconds = 1)),
                    )
                    contenderTimedOut.countDown()
                    assertEquals(AcquireSessionLockResult.TimedOut, timedOut)
                    if (!ownerReleased.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(IllegalStateException("Owner did not finish releasing the lock."))
                    } else {
                        val acquiredAfterRelease = operationValue(
                            scope.acquireSessionAdvisoryLock(acquireAst(resource)),
                        )
                        val releasedAfterAcquire = if (acquiredAfterRelease == AcquireSessionLockResult.Acquired) {
                            operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                        } else {
                            null
                        }
                        ExecuteResult.Success(
                            ContenderOutcome(timedOut, acquiredAfterRelease, releasedAfterAcquire),
                        )
                    }
                }
            }

            assertTrue(contenderTimedOut.await(WAIT_SECONDS, TimeUnit.SECONDS), "Contender did not finish its wait")
            allowOwnerRelease.countDown()

            assertEquals(ReleaseSessionLockResult.Released, successValue(await(owner)))
            val outcome = successValue(await(contender))
            assertEquals(AcquireSessionLockResult.TimedOut, outcome.first)
            assertEquals(AcquireSessionLockResult.Acquired, outcome.second)
            assertEquals(ReleaseSessionLockResult.Released, outcome.third)
        } finally {
            allowOwnerRelease.countDown()
            shutdown(pool)
        }
    }

    @Test
    fun release_from_another_connection_does_not_release_the_owners_lock() {
        val resource = uniqueResource("ownership")
        val ownerAcquired = CountDownLatch(1)
        val allowOwnerRelease = CountDownLatch(1)
        val ownerReleased = CountDownLatch(1)
        val contenderCheckedOwnership = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val owner = pool.submit<ExecuteResult<ReleaseSessionLockResult>> {
                try {
                    withOperationConnection { scope ->
                        val acquired = operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource)))
                        assertEquals(AcquireSessionLockResult.Acquired, acquired)
                        ownerAcquired.countDown()
                        if (!allowOwnerRelease.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Owner release was not requested in time."))
                        } else {
                            val released = operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                            ownerReleased.countDown()
                            ExecuteResult.Success(released)
                        }
                    }
                } finally {
                    ownerReleased.countDown()
                }
            }

            assertTrue(ownerAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS), "Owner did not acquire the lock")

            val contender = pool.submit<ExecuteResult<ContenderOwnershipOutcome>> {
                withOperationConnection { scope ->
                    try {
                        val otherConnectionRelease = operationValue(
                            scope.releaseSessionAdvisoryLock(releaseAst(resource)),
                        )
                        val stillHeld = operationValue(
                            scope.acquireSessionAdvisoryLock(acquireAst(resource, timeoutSeconds = 1)),
                        )
                        assertEquals(ReleaseSessionLockResult.NotOwned, otherConnectionRelease)
                        assertEquals(AcquireSessionLockResult.TimedOut, stillHeld)
                        contenderCheckedOwnership.countDown()
                        if (!ownerReleased.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Owner did not finish releasing the lock."))
                        } else {
                            val afterRelease = operationValue(
                                scope.acquireSessionAdvisoryLock(acquireAst(resource)),
                            )
                            val cleanupRelease = if (afterRelease == AcquireSessionLockResult.Acquired) {
                                operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                            } else {
                                null
                            }
                            ExecuteResult.Success(
                                ContenderOwnershipOutcome(otherConnectionRelease, stillHeld, afterRelease, cleanupRelease),
                            )
                        }
                    } finally {
                        contenderCheckedOwnership.countDown()
                    }
                }
            }

            assertTrue(
                contenderCheckedOwnership.await(WAIT_SECONDS, TimeUnit.SECONDS),
                "Contender did not verify lock ownership",
            )
            allowOwnerRelease.countDown()

            assertEquals(ReleaseSessionLockResult.Released, successValue(await(owner)))
            val outcome = successValue(await(contender))
            assertEquals(ReleaseSessionLockResult.NotOwned, outcome.otherConnectionRelease)
            assertEquals(AcquireSessionLockResult.TimedOut, outcome.ownerStillHeld)
            assertEquals(AcquireSessionLockResult.Acquired, outcome.afterOwnerRelease)
            assertEquals(ReleaseSessionLockResult.Released, outcome.cleanupRelease)
        } finally {
            allowOwnerRelease.countDown()
            shutdown(pool)
        }
    }

    @Test
    fun session_lock_survives_a_commit_on_the_same_operation_connection() {
        val resource = uniqueResource("commit")
        val ownerAcquired = CountDownLatch(1)
        val commitCompleted = CountDownLatch(1)
        val allowOwnerRelease = CountDownLatch(1)
        val ownerReleased = CountDownLatch(1)
        val contenderTimedOut = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)

        try {
            val owner = pool.submit<ExecuteResult<ReleaseSessionLockResult>> {
                try {
                    withOperationConnection { scope ->
                        val acquired = operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource)))
                        assertEquals(AcquireSessionLockResult.Acquired, acquired)
                        ownerAcquired.countDown()
                        val committed = scope.transaction { ExecuteResult.Success(Unit) }
                        successValue(committed)
                        commitCompleted.countDown()
                        if (!allowOwnerRelease.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Owner release was not requested in time."))
                        } else {
                            val released = operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                            ownerReleased.countDown()
                            ExecuteResult.Success(released)
                        }
                    }
                } finally {
                    ownerReleased.countDown()
                }
            }

            assertTrue(ownerAcquired.await(WAIT_SECONDS, TimeUnit.SECONDS), "Owner did not acquire the lock")
            val contender = pool.submit<ExecuteResult<ContenderOutcome>> {
                withOperationConnection { scope ->
                    if (!commitCompleted.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(IllegalStateException("Owner transaction did not commit."))
                    } else {
                        val timedOut = operationValue(
                            scope.acquireSessionAdvisoryLock(acquireAst(resource, timeoutSeconds = 1)),
                        )
                        contenderTimedOut.countDown()
                        assertEquals(AcquireSessionLockResult.TimedOut, timedOut)
                        if (!ownerReleased.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Owner did not finish releasing the lock."))
                        } else {
                            val acquiredAfterRelease = operationValue(
                                scope.acquireSessionAdvisoryLock(acquireAst(resource)),
                            )
                            val releasedAfterAcquire = if (acquiredAfterRelease == AcquireSessionLockResult.Acquired) {
                                operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                            } else {
                                null
                            }
                            ExecuteResult.Success(
                                ContenderOutcome(timedOut, acquiredAfterRelease, releasedAfterAcquire),
                            )
                        }
                    }
                }
            }

            assertTrue(contenderTimedOut.await(WAIT_SECONDS, TimeUnit.SECONDS), "Lock did not remain held after commit")
            allowOwnerRelease.countDown()

            assertEquals(ReleaseSessionLockResult.Released, successValue(await(owner)))
            val outcome = successValue(await(contender))
            assertEquals(AcquireSessionLockResult.TimedOut, outcome.first)
            assertEquals(AcquireSessionLockResult.Acquired, outcome.second)
            assertEquals(ReleaseSessionLockResult.Released, outcome.third)
        } finally {
            allowOwnerRelease.countDown()
            shutdown(pool)
        }
    }

    @Test
    fun session_lock_survives_rollback_and_connection_close_releases_it() {
        val resource = uniqueResource("rollback-close")
        val rollbackCompleted = CountDownLatch(1)
        val ownerClosed = CountDownLatch(1)
        val contenderTimedOut = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val rollbackFailure = IllegalStateException("intentional rollback for advisory-lock integration test")

        try {
            val owner = pool.submit<ExecuteResult<Unit>> {
                try {
                    withOperationConnection { scope ->
                        val acquired = operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource)))
                        assertEquals(AcquireSessionLockResult.Acquired, acquired)
                        val rolledBack = scope.transaction { ExecuteResult.Failure(rollbackFailure) }
                        val transactionFailure = assertIs<ExecuteResult.Failure>(rolledBack)
                        assertEquals(rollbackFailure, transactionFailure.exception)
                        rollbackCompleted.countDown()
                        if (!contenderTimedOut.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Contender did not observe the held lock."))
                        } else {
                            // The failed transaction latches this parent Scope. Returning lets Operation close the
                            // owning Connection, which is the required cleanup path for the still-held Session lock.
                            ExecuteResult.Success(Unit)
                        }
                    }
                } finally {
                    ownerClosed.countDown()
                }
            }

            val contender = pool.submit<ExecuteResult<ContenderOutcome>> {
                withOperationConnection { scope ->
                    if (!rollbackCompleted.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                        ExecuteResult.Failure(IllegalStateException("Owner transaction did not roll back."))
                    } else {
                        val timedOut = operationValue(
                            scope.acquireSessionAdvisoryLock(acquireAst(resource, timeoutSeconds = 1)),
                        )
                        contenderTimedOut.countDown()
                        assertEquals(AcquireSessionLockResult.TimedOut, timedOut)
                        if (!ownerClosed.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                            ExecuteResult.Failure(IllegalStateException("Owner Connection was not closed."))
                        } else {
                            val acquiredAfterClose = operationValue(
                                scope.acquireSessionAdvisoryLock(acquireAst(resource)),
                            )
                            val releasedAfterAcquire = if (acquiredAfterClose == AcquireSessionLockResult.Acquired) {
                                operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
                            } else {
                                null
                            }
                            ExecuteResult.Success(
                                ContenderOutcome(timedOut, acquiredAfterClose, releasedAfterAcquire),
                            )
                        }
                    }
                }
            }

            assertTrue(contenderTimedOut.await(WAIT_SECONDS, TimeUnit.SECONDS), "Lock did not remain held after rollback")
            val ownerResult = await(owner)
            val ownerFailure = assertIs<ExecuteResult.Failure>(ownerResult)
            assertEquals(rollbackFailure, ownerFailure.exception)
            val outcome = successValue(await(contender))
            assertEquals(AcquireSessionLockResult.TimedOut, outcome.first)
            assertEquals(AcquireSessionLockResult.Acquired, outcome.second)
            assertEquals(ReleaseSessionLockResult.Released, outcome.third)
        } finally {
            contenderTimedOut.countDown()
            shutdown(pool)
        }
    }

    @Test
    fun a_successful_operation_connection_close_releases_its_lock() {
        val resource = uniqueResource("connection-close")
        val ownerResult = withOperationConnection { scope ->
            ExecuteResult.Success(operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource))))
        }
        assertEquals(AcquireSessionLockResult.Acquired, successValue(ownerResult))

        val contenderResult = withOperationConnection { scope ->
            val acquired = operationValue(scope.acquireSessionAdvisoryLock(acquireAst(resource)))
            val released = if (acquired == AcquireSessionLockResult.Acquired) {
                operationValue(scope.releaseSessionAdvisoryLock(releaseAst(resource)))
            } else {
                null
            }
            ExecuteResult.Success(acquired to released)
        }
        val outcome = successValue(contenderResult)
        assertEquals(AcquireSessionLockResult.Acquired, outcome.first)
        assertEquals(ReleaseSessionLockResult.Released, outcome.second)
    }

    private fun <T> withOperationConnection(
        block: (OperationExecutionScope) -> ExecuteResult<T>,
    ): ExecuteResult<T> {
        val context = settings.context(server)
        return try {
            OperationExecutionExecutor(context).connection(block)
        } finally {
            context.close()
        }
    }

    private fun acquireAst(
        resource: LockResource,
        timeoutSeconds: Int? = null,
    ) = acquireSessionAdvisoryLock {
        resource(resource.namespace, resource.name)
        if (timeoutSeconds == null) noWait() else waitTimeout(timeoutSeconds.seconds)
    }.ast

    private fun releaseAst(resource: LockResource) = releaseSessionAdvisoryLock {
        resource(resource.namespace, resource.name)
    }.ast

    private fun uniqueResource(testName: String) = LockResource(
        namespace = "operation-session-integration",
        name = "$testName-${UUID.randomUUID()}",
    )

    private fun <T : Any> operationValue(result: ExecuteResult<T>): T = when (result) {
        is ExecuteResult.Success -> requireNotNull(result.result) { "Operation returned a null success value." }
        is ExecuteResult.Failure -> throw result.exception
    }

    private fun <T : Any> successValue(result: ExecuteResult<T>): T = when (result) {
        is ExecuteResult.Success -> requireNotNull(result.result) { "Operation returned a null success value." }
        is ExecuteResult.Failure -> throw AssertionError("Expected an Operation success result.", result.exception)
    }

    private fun <T> await(future: Future<T>): T = future.get(WAIT_SECONDS, TimeUnit.SECONDS)

    private fun shutdown(pool: ExecutorService) {
        pool.shutdownNow()
        assertTrue(pool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS), "Integration worker threads did not stop")
    }

    private data class ContenderOutcome(
        val first: AcquireSessionLockResult,
        val second: AcquireSessionLockResult,
        val third: ReleaseSessionLockResult?,
    )

    private data class ContenderOwnershipOutcome(
        val otherConnectionRelease: ReleaseSessionLockResult,
        val ownerStillHeld: AcquireSessionLockResult,
        val afterOwnerRelease: AcquireSessionLockResult,
        val cleanupRelease: ReleaseSessionLockResult?,
    )

    private data class InspectedDatabaseServer(
        val info: DatabaseServerInfo,
        val driverName: String,
        val driverVersion: String,
    ) {
        val operationDialect: DatabaseDialect
            get() = when (info.product) {
                DatabaseProduct.MYSQL -> DatabaseDialect.MYSQL
                DatabaseProduct.MARIA_DB -> DatabaseDialect.MARIA_DB
            }
    }

    private data class MySqlIntegrationSettings(
        val domain: String,
        val host: String,
        val port: Int,
        val schema: String,
        val username: String,
        val password: String,
    ) {
        val jdbcUrl: String get() = "$domain:$port/$schema?serverTimezone=UTC"

        fun inspectServer(): InspectedDatabaseServer =
            DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT VERSION(), @@version_comment, DATABASE(), CURRENT_USER()").use { rows ->
                        check(rows.next()) { "The database server did not return its identity row." }
                        val versionText = requireNotNull(rows.getString(1))
                        val versionComment = requireNotNull(rows.getString(2))
                        val selectedSchema = rows.getString(3)
                        val connectionAccount = requireNotNull(rows.getString(4)).substringBefore('@')
                        check(!connectionAccount.equals("root", ignoreCase = true)) {
                            "Session integration tests require a non-root database account."
                        }
                        check(selectedSchema == schema) {
                            "Connected Schema did not match the explicitly selected disposable Example Database."
                        }
                        val identifiesAsMariaDb = versionText.contains("MariaDB", ignoreCase = true)
                        check(identifiesAsMariaDb == versionComment.contains("MariaDB", ignoreCase = true)) {
                            "The database server version and product comment did not identify the same product."
                        }
                        check(
                            identifiesAsMariaDb || versionComment.contains("MySQL", ignoreCase = true),
                        ) {
                            "Only MySQL and MariaDB are supported by this integration test."
                        }
                        check(!rows.next()) { "The server identity query returned an unexpected extra row." }
                        InspectedDatabaseServer(
                            info = DatabaseServerInfo.parse(versionText),
                            driverName = connection.metaData.driverName,
                            driverVersion = connection.metaData.driverVersion,
                        )
                    }
                }
            }

        fun context(server: InspectedDatabaseServer) = DatabaseContext(
            name = "session-advisory-lock-integration",
            config = DatabaseConfig(
                connection = DatabaseConnectionConfig(
                    dbDomain = domain,
                    dbPort = port,
                    dbName = "$schema?serverTimezone=UTC",
                    dbUserName = username,
                    dbPassword = password,
                ),
                platform = DatabasePlatformConfig(
                    dialect = server.operationDialect,
                    targetDatabaseVersion = server.info.version,
                ),
            ),
        )

        companion object {
            fun fromEnvironment(): MySqlIntegrationSettings {
                val configuredDomain = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_DOMAIN")) {
                    "KOTLIN_DB_OPERATION_IT_DOMAIN must be provided by the local .env or process environment."
                }
                val host = Regex("^jdbc:mysql://(localhost|127\\.0\\.0\\.1)$")
                    .matchEntire(configuredDomain)?.groupValues?.get(1)
                    ?: error("KOTLIN_DB_OPERATION_IT_DOMAIN must point to localhost or 127.0.0.1.")
                val domain = "jdbc:mysql://$host"
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
                    "Session integration tests require a non-root Database account."
                }
                val password = requireNotNull(System.getenv("KOTLIN_DB_OPERATION_IT_PASSWORD")) {
                    "KOTLIN_DB_OPERATION_IT_PASSWORD must be provided by the local .env or process environment."
                }
                require(password.isNotEmpty()) { "KOTLIN_DB_OPERATION_IT_PASSWORD must not be empty." }
                return MySqlIntegrationSettings(domain, host, port, schema, username, password)
            }
        }
    }

    private companion object {
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
