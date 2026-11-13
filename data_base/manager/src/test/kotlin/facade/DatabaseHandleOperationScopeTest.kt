package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseHandleOperationScopeTest {
    private object Primary : DatabaseKey

    @Test
    fun `watch is handle scoped, does not open a connection, and observes the managed operation`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val events = mutableListOf<OperationDatabaseEvent>()

        try {
            val subscription = handle.watch { event ->
                if (event is OperationDatabaseEvent) events += event
            }
            assertEquals(0, provider.openCount.get())

            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertEquals(
                listOf(
                    OperationDatabaseEventType.CONNECTION_OPENED,
                    OperationDatabaseEventType.CONNECTION_CLOSED,
                ),
                events.map(OperationDatabaseEvent::type),
            )

            subscription.close()
            events.clear()
            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertTrue(events.isEmpty())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `multiple watchers are isolated and closing one leaves the other active`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var firstCount = 0
        var secondCount = 0

        try {
            val first = handle.watch {
                firstCount++
                throw AssertionError("observer failure")
            }
            val second = handle.watch {
                secondCount++
            }

            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertTrue(firstCount > 0)
            assertTrue(secondCount > 0)

            first.close()
            val previousSecondCount = secondCount
            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertEquals(previousSecondCount + 2, secondCount)
            second.close()
        } finally {
            manager.close()
        }
    }

    @Test
    fun `scope API exposes the managed root scope and connection through callback`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var callbackScope: OperationExecutionScope? = null

        try {
            val result = handle.withOperationScope { scope ->
                provider.events += "callback-start"
                callbackScope = scope
                assertSame(provider.connections.single(), scope.connection)
                assertNull(scope.rejectOperation())
                provider.events += "callback-end"
                ExecuteResult.Success(scope)
            }

            val success = assertIs<ExecuteResult.Success<*>>(result)
            assertSame(callbackScope, success.result)
            assertEquals(listOf("callback-start", "callback-end", "connection-close"), provider.events)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(0, provider.autoCommitChanges.get())
            assertEquals(0, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
            assertTrue(provider.connections.single().isClosed)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `root scope can start a child transaction on the same managed connection`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]

        try {
            val result = handle.withOperationScope { rootScope ->
                val rootConnection = rootScope.connection
                val transactionResult = rootScope.transaction { childScope ->
                    assertSame(rootConnection, childScope.connection)
                    ExecuteResult.Success(Unit)
                }
                assertIs<ExecuteResult.Success<*>>(transactionResult)
                ExecuteResult.Success(Unit)
            }

            assertEquals(ExecuteResult.Success(Unit), result)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `callback failure remains primary when connection cleanup also fails`() {
        val cleanupFailure = SQLException("synthetic close failure")
        val provider = TrackingProvider(closeFailure = cleanupFailure)
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val callbackFailure = IllegalStateException("synthetic callback failure")
        var callbackCount = 0

        try {
            val result = handle.withOperationScope<Unit> {
                callbackCount++
                throw callbackFailure
            }

            val failure = assertIs<ExecuteResult.Failure>(result)
            assertSame(callbackFailure, failure.exception)
            assertSame(cleanupFailure, callbackFailure.suppressed.single())
            assertEquals(1, callbackCount)
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `cleanup failure after successful callback is returned and handle remains reusable`() {
        val provider = TrackingProvider(closeFailure = SQLException("synthetic close failure"))
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var callbackCount = 0

        try {
            val first = handle.withOperationScope {
                callbackCount++
                ExecuteResult.Success("completed")
            }
            assertIs<ExecuteResult.Failure>(first)

            val second = handle.withOperationScope {
                callbackCount++
                ExecuteResult.Success("completed again")
            }
            assertIs<ExecuteResult.Failure>(second)

            assertEquals(2, callbackCount)
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `fatal callback error propagates after connection cleanup`() {
        val cleanupFailure = SQLException("synthetic close failure")
        val provider = TrackingProvider(closeFailure = cleanupFailure)
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val fatal = AssertionError("synthetic fatal error")
        var callbackCount = 0

        try {
            val thrown = assertFailsWith<AssertionError> {
                handle.withOperationScope<Unit> {
                    callbackCount++
                    throw fatal
                }
            }

            assertSame(fatal, thrown)
            assertSame(cleanupFailure, fatal.suppressed.single())
            assertEquals(1, callbackCount)
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `existing connection and transaction APIs retain their behavior`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]

        try {
            assertEquals(
                ExecuteResult.Success("connection"),
                handle.connection { ExecuteResult.Success("connection") },
            )
            assertEquals(
                ExecuteResult.Success("transaction"),
                handle.transaction { ExecuteResult.Success("transaction") },
            )

            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `legacy custom handle compiles and keeps old APIs without the new capability`() {
        val legacy = LegacyHandle()

        assertEquals(ExecuteResult.Success("legacy connection"), legacy.connection {
            ExecuteResult.Success("legacy connection")
        })
        assertEquals(ExecuteResult.Success("legacy transaction"), legacy.transaction {
            ExecuteResult.Success("legacy transaction")
        })
        val unsupported = assertIs<ExecuteResult.Failure>(legacy.withOperationScope {
            ExecuteResult.Success(Unit)
        })
        assertIs<UnsupportedOperationException>(unsupported.exception)
        assertFailsWith<UnsupportedOperationException> {
            legacy.watch { }
        }
        assertFalse(DatabaseHandleApi::class.java.methods.any { it.name == "withOperationScope" })
    }

    @Test
    fun `concurrent scope operations remain independently admitted`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val bothEntered = CountDownLatch(2)
        val continueCallbacks = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val results = (1..2).map {
                workers.submit<ExecuteResult<Int>> {
                    handle.withOperationScope {
                        bothEntered.countDown()
                        check(bothEntered.await(5, TimeUnit.SECONDS))
                        check(continueCallbacks.await(5, TimeUnit.SECONDS))
                        ExecuteResult.Success(1)
                    }
                }
            }

            assertTrue(bothEntered.await(5, TimeUnit.SECONDS))
            continueCallbacks.countDown()
            results.forEach { assertEquals(ExecuteResult.Success(1), it.get(5, TimeUnit.SECONDS)) }
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            continueCallbacks.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    private fun manager(provider: TrackingProvider): DatabaseManager {
        val databaseConfig = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://127.0.0.1",
                dbPort = 3306,
                dbName = "manager_scope_test",
                dbUserName = "user",
                dbPassword = "password",
            ),
        )
        return DatabaseManager(
            { database(Primary) { config = databaseConfig } },
            { name, config, identity -> DatabaseContext(name, config, provider, identity) },
        )
    }

    private class LegacyHandle : DatabaseHandleApi {
        override val key: DatabaseKey = Primary
        private val executor = Proxy.newProxyInstance(
            IQueryBuilderExecutor::class.java.classLoader,
            arrayOf(IQueryBuilderExecutor::class.java),
        ) { _, method, _ -> defaultValue(method.returnType) } as IQueryBuilderExecutor

        override fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            block(executor)

        override fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            block(executor)
    }

    private class TrackingProvider(
        private val closeFailure: SQLException? = null,
    ) : IDatabaseConnection {
        val openCount = AtomicInteger()
        val connectionCloseCount = AtomicInteger()
        val autoCommitChanges = AtomicInteger()
        val commitCount = AtomicInteger()
        val rollbackCount = AtomicInteger()
        val connections = CopyOnWriteArrayList<Connection>()
        val events = CopyOnWriteArrayList<String>()

        override fun build(): Connection {
            openCount.incrementAndGet()
            val closed = AtomicBoolean(false)
            val connection = Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "close" -> {
                        connectionCloseCount.incrementAndGet()
                        events += "connection-close"
                        closed.set(true)
                        closeFailure?.let { throw it }
                        null
                    }
                    "isClosed" -> closed.get()
                    "getAutoCommit" -> true
                    "setAutoCommit" -> {
                        autoCommitChanges.incrementAndGet()
                        null
                    }
                    "commit" -> {
                        commitCount.incrementAndGet()
                        null
                    }
                    "rollback" -> {
                        rollbackCount.incrementAndGet()
                        null
                    }
                    "toString" -> "ManagerScopeTestConnection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> defaultValue(method.returnType)
                }
            } as Connection
            connections += connection
            return connection
        }
    }

    companion object {
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
}
