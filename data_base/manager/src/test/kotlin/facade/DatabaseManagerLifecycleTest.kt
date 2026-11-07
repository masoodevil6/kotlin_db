package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseManagerLifecycleTest {
    private object Primary : DatabaseKey
    private object Analytics : DatabaseKey
    @Test
    fun shutdownWaitsForAnActiveConnectionScopeAndRejectsNewHandleWork() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = ControlledProvider()
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operation = workers.submit<ExecuteResult<String>> {
                handle.connection {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Success("query-finished")
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)

            assertEquals(0, provider.providerCloseCount.get())
            assertIs<ExecuteResult.Failure>(handle.connection { ExecuteResult.Success("rejected") })
            assertEquals(1, provider.openCount.get())

            release.countDown()
            assertEquals(ExecuteResult.Success("query-finished"), operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, provider.providerCloseCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun shutdownWaitsForAnActiveTransactionScope() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = ControlledProvider()
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operation = workers.submit<ExecuteResult<String>> {
                handle.transaction {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Success("transaction-finished")
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)

            assertEquals(0, provider.providerCloseCount.get())
            release.countDown()
            assertEquals(ExecuteResult.Success("transaction-finished"), operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, provider.commitCount.get())
            assertEquals(1, provider.providerCloseCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun shutdownRejectsAllNewHandleCapabilitiesAfterAdmissionCloses() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = ControlledProvider()
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val active = workers.submit<ExecuteResult<Unit>> {
                handle.connection {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Success(Unit)
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)

            assertIs<ExecuteResult.Failure>(handle.connection { ExecuteResult.Success(Unit) })
            assertIs<ExecuteResult.Failure>(handle.transaction { ExecuteResult.Success(Unit) })
            assertEquals(1, provider.openCount.get())

            release.countDown()
            assertEquals(ExecuteResult.Success(Unit), active.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun acceptedOperationCompletesBeforeManagerClosesItsContext() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = ControlledProvider()
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operation = workers.submit<ExecuteResult<String>> {
                handle.connection {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Success("accepted")
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)
            assertEquals(0, provider.providerCloseCount.get())

            release.countDown()
            assertEquals(ExecuteResult.Success("accepted"), operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun concurrentOperationsShareOneContextWithoutSerializingTheirScopes() {
        val contextCreations = AtomicInteger()
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val provider = ControlledProvider()
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig ->
                contextCreations.incrementAndGet()
                DatabaseContext(name, databaseConfig, provider)
            },
        )
        val first = manager[Primary]
        val second = manager[Primary]
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operations = listOf(first, second).map { handle ->
                workers.submit<ExecuteResult<Unit>> {
                    handle.connection {
                        entered.countDown()
                        await(release)
                        ExecuteResult.Success(Unit)
                    }
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            assertEquals(1, contextCreations.get())
            assertEquals(2, provider.openCount.get())
            release.countDown()
            operations.forEach { assertEquals(ExecuteResult.Success(Unit), it.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) }
            manager.shutdown()
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun shutdownClosesEveryRegisteredContext() {
        val primary = ControlledProvider()
        val analytics = ControlledProvider()
        val manager = DatabaseManager(
            {
                database(Primary) { config = config("primary") }
                database(Analytics) { config = config("analytics") }
            },
            { name, databaseConfig ->
                val provider = if (databaseConfig.connection.dbName == "primary") primary else analytics
                DatabaseContext(name, databaseConfig, provider)
            },
        )

        assertEquals(ExecuteResult.Success(Unit), manager[Primary].connection { ExecuteResult.Success(Unit) })
        assertEquals(ExecuteResult.Success(Unit), manager[Analytics].connection { ExecuteResult.Success(Unit) })
        manager.shutdown()

        assertEquals(1, primary.providerCloseCount.get())
        assertEquals(1, analytics.providerCloseCount.get())
        assertFailsWith<IllegalStateException> { manager.registeredKeys() }
    }

    @Test
    fun shutdownRacingWithLazyContextInitializationWaitsForAcceptedOperation() {
        val factoryEntered = CountDownLatch(1)
        val allowFactory = CountDownLatch(1)
        val provider = ControlledProvider()
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig ->
                factoryEntered.countDown()
                await(allowFactory)
                DatabaseContext(name, databaseConfig, provider)
            },
        )
        val handle = manager[Primary]
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operation = workers.submit<ExecuteResult<Unit>> {
                handle.connection { ExecuteResult.Success(Unit) }
            }
            assertTrue(factoryEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)
            assertEquals(0, provider.providerCloseCount.get())

            allowFactory.countDown()
            assertEquals(ExecuteResult.Success(Unit), operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            allowFactory.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun repeatedConcurrentShutdownCallsWaitAndCloseContextsOnlyOnce() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val provider = ControlledProvider()
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(3)

        try {
            val operation = workers.submit<ExecuteResult<Unit>> {
                handle.connection {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Success(Unit)
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val firstShutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)
            val secondShutdown = startShutdown(workers, manager)

            release.countDown()
            assertEquals(ExecuteResult.Success(Unit), operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            firstShutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            secondShutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun concurrentShutdownCallersObserveTheSameContextCloseFailure() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closeFailure = IllegalStateException("shared provider close failure")
        val provider = ControlledProvider(closeFailure = closeFailure)
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(3)

        try {
            val operation = workers.submit<ExecuteResult<Unit>> {
                handle.connection {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Success(Unit)
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val firstShutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)

            val secondThread = AtomicReference<Thread>()
            val secondStarted = CountDownLatch(1)
            val secondShutdown = workers.submit {
                secondThread.set(Thread.currentThread())
                secondStarted.countDown()
                manager.shutdown()
            }
            assertTrue(secondStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            awaitThreadWaiting(secondThread)

            release.countDown()
            assertEquals(ExecuteResult.Success(Unit), operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val firstError = assertFailsWith<ExecutionException> {
                firstShutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            val secondError = assertFailsWith<ExecutionException> {
                secondShutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            assertSame(closeFailure, firstError.cause)
            assertSame(closeFailure, secondError.cause)
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun operationFailureDoesNotPreventShutdownFromClosingItsContext() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failure = IllegalStateException("synthetic query failure")
        val closeFailure = IllegalArgumentException("synthetic provider close failure")
        val provider = ControlledProvider(closeFailure = closeFailure)
        val (manager, handle) = manager(provider)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operation = workers.submit<ExecuteResult<Unit>> {
                handle.connection {
                    entered.countDown()
                    await(release)
                    ExecuteResult.Failure(failure)
                }
            }
            assertTrue(entered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)
            assertEquals(0, provider.providerCloseCount.get())

            release.countDown()
            val result = operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
            val shutdownError = assertFailsWith<ExecutionException> {
                shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            }
            assertSame(closeFailure, shutdownError.cause)
            assertEquals(1, provider.providerCloseCount.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun contextInitializationFailureReleasesItsLeaseSoShutdownCanFinish() {
        val factoryEntered = CountDownLatch(1)
        val allowFailure = CountDownLatch(1)
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { _, _ ->
                factoryEntered.countDown()
                await(allowFailure)
                throw IllegalStateException("synthetic context initialization failure")
            },
        )
        val handle = manager[Primary]
        val workers = Executors.newFixedThreadPool(2)

        try {
            val operation = workers.submit<ExecuteResult<Unit>> {
                handle.connection { ExecuteResult.Success(Unit) }
            }
            assertTrue(factoryEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            val shutdown = startShutdown(workers, manager)
            awaitShutdownStart(manager)
            allowFailure.countDown()

            assertIs<ExecuteResult.Failure>(operation.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            shutdown.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            assertFailsWith<IllegalStateException> { manager.registeredKeys() }
        } finally {
            allowFailure.countDown()
            workers.shutdownNow()
            manager.shutdown()
        }
    }

    @Test
    fun contextCloseFailureDoesNotSkipCleanupOfOtherContexts() {
        val firstFailure = IllegalStateException("first provider close failed")
        val secondFailure = IllegalArgumentException("second provider close failed")
        val primary = ControlledProvider(closeFailure = firstFailure)
        val analytics = ControlledProvider(closeFailure = secondFailure)
        val manager = DatabaseManager(
            {
                database(Primary) { config = config("primary") }
                database(Analytics) { config = config("analytics") }
            },
            { name, databaseConfig ->
                val provider = if (databaseConfig.connection.dbName == "primary") primary else analytics
                DatabaseContext(name, databaseConfig, provider)
            },
        )
        assertEquals(ExecuteResult.Success(Unit), manager[Primary].connection { ExecuteResult.Success(Unit) })
        assertEquals(ExecuteResult.Success(Unit), manager[Analytics].connection { ExecuteResult.Success(Unit) })

        val thrown = assertFailsWith<IllegalStateException> { manager.shutdown() }
        assertSame(firstFailure, thrown)
        assertEquals(listOf(secondFailure), thrown.suppressed.toList())
        assertEquals(1, primary.providerCloseCount.get())
        assertEquals(1, analytics.providerCloseCount.get())
        manager.shutdown()
    }

    @Test
    fun shutdownFromInsideAnActiveHandleCallbackFailsFastInsteadOfDeadlocking() {
        val provider = ControlledProvider()
        val (manager, handle) = manager(provider)

        val result = handle.connection {
            assertFailsWith<IllegalStateException> { manager.shutdown() }
            ExecuteResult.Success("still-open")
        }

        assertEquals(ExecuteResult.Success("still-open"), result)
        assertEquals(0, provider.providerCloseCount.get())
        manager.shutdown()
        assertEquals(1, provider.providerCloseCount.get())
    }

    private fun manager(
        provider: ControlledProvider,
    ): Pair<DatabaseManager, DatabaseHandle> {
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig -> DatabaseContext(name, databaseConfig, provider) },
        )
        return manager to manager[Primary]
    }

    private fun config(name: String) = DatabaseConfig(
        connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, name, "user", "secret"),
    )

    private class ControlledProvider(
        private val blockStatement: (() -> Unit)? = null,
        private val closeFailure: Throwable? = null,
    ) : IDatabaseConnection, AutoCloseable {
        val openCount = AtomicInteger()
        val connectionCloseCount = AtomicInteger()
        val providerCloseCount = AtomicInteger()
        val commitCount = AtomicInteger()
        val rollbackCount = AtomicInteger()
        val statementCloseCount = AtomicInteger()

        override fun build(): Connection {
            openCount.incrementAndGet()
            val closed = AtomicBoolean(false)
            return Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "close" -> {
                        if (closed.compareAndSet(false, true)) connectionCloseCount.incrementAndGet()
                        null
                    }
                    "isClosed" -> closed.get()
                    "getAutoCommit" -> true
                    "setAutoCommit" -> null
                    "commit" -> { commitCount.incrementAndGet(); null }
                    "rollback" -> { rollbackCount.incrementAndGet(); null }
                    "prepareStatement" -> preparedStatement(requireNotNull(args?.firstOrNull() as? String))
                    "toString" -> "ControlledConnection"
                    "hashCode" -> System.identityHashCode(this)
                    "equals" -> false
                    else -> defaultValue(method.returnType)
                }
            } as Connection
        }

        private fun preparedStatement(sql: String): PreparedStatement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "execute", "executeUpdate", "executeLargeUpdate", "executeQuery" -> {
                    blockStatement?.invoke()
                    when (method.name) {
                        "execute" -> true
                        "executeUpdate" -> 1
                        "executeLargeUpdate" -> 1L
                        else -> null
                    }
                }
                "close" -> { statementCloseCount.incrementAndGet(); null }
                "toString" -> "ControlledStatement($sql)"
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        override fun close() {
            providerCloseCount.incrementAndGet()
            closeFailure?.let { throw it }
        }

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0.0f
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }

    private fun startShutdown(workers: ExecutorService, manager: DatabaseManager): Future<*> =
        workers.submit { manager.shutdown() }

    /** Uses the public admission boundary to observe CLOSING while an accepted operation is held. */
    private fun awaitShutdownStart(manager: DatabaseManager) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            try {
                manager.registeredKeys()
            } catch (_: IllegalStateException) {
                return
            }
            Thread.yield()
        }
        throw AssertionError("DatabaseManager shutdown did not close admission before the deadline")
    }

    private fun awaitThreadWaiting(thread: AtomicReference<Thread>) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
        while (System.nanoTime() < deadline) {
            if (thread.get()?.state == Thread.State.WAITING) return
            Thread.yield()
        }
        throw AssertionError("Concurrent shutdown caller did not wait for the first cleanup")
    }

    private fun await(latch: CountDownLatch) {
        check(latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "Timed out waiting for lifecycle test latch" }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
