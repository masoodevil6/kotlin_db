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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseCatalogTest {
    private object Primary : DatabaseKey
    private object Analytics : DatabaseKey
    private object Unregistered : DatabaseKey
    private data object DataSingleton : DatabaseKey
    private data class ValueKey(val name: String) : DatabaseKey

    private class OrdinaryKey(val name: String) : DatabaseKey

    @Test
    fun typedRegistrationIsLazyAndResolvesTheSameHandleIdentity() {
        val contextCreations = AtomicInteger()
        val provider = TrackingProvider()
        val manager = DatabaseManager(
            {
                database(Primary) { config = config("primary") }
            },
            { name, databaseConfig ->
                contextCreations.incrementAndGet()
                DatabaseContext(name, databaseConfig, provider)
            },
        )

        assertEquals(setOf(Primary), manager.registeredKeys())
        assertEquals(0, contextCreations.get())
        val first = manager[Primary]
        val second = manager.get(Primary)
        assertNotSame(first, second)
        assertEquals(0, contextCreations.get())

        val result = first.connection { ExecuteResult.Success("done") }

        assertEquals(ExecuteResult.Success("done"), result)
        assertEquals(1, contextCreations.get())
        assertEquals(1, provider.openCount.get())
        assertEquals(1, provider.connectionCloseCount.get())

        assertEquals(ExecuteResult.Success("committed"), second.transaction {
            ExecuteResult.Success("committed")
        })
        assertEquals(1, contextCreations.get()) // The same registration reuses its lazily-created Context.
        assertEquals(2, provider.openCount.get())
        assertEquals(1, provider.commitCount.get())
        assertEquals(2, provider.connectionCloseCount.get())
        manager.close()
        assertEquals(1, provider.closeCount.get())
    }

    @Test
    fun singletonObjectsAndDataObjectsAreUsableAsStableKeys() {
        val manager = DatabaseManager {
            database(Primary) { config = config("primary") }
            database(DataSingleton) { config = config("data-singleton") }
        }

        assertEquals(listOf(Primary, DataSingleton), manager.registeredKeys().toList())
        assertEquals(Primary, manager[Primary].key)
        assertEquals(DataSingleton, manager[DataSingleton].key)
        manager.close()
    }

    @Test
    fun duplicateAndUnknownKeysAreRejectedWithoutCreatingContexts() {
        val duplicate = assertFailsWith<IllegalArgumentException> {
            DatabaseManager {
                database(Primary) { config = config("primary") }
                database(Primary) { config = config("other") }
            }
        }
        assertTrue(duplicate.message.orEmpty().contains("Primary"))

        val manager = DatabaseManager()
        val unknown = assertFailsWith<NoSuchElementException> { manager.get(Unregistered) }
        assertTrue(unknown.message.orEmpty().contains("Unregistered"))
        manager.close()
    }

    @Test
    fun equalValueKeysIdentifyOneRegistrationForDuplicatesLookupAndContextCaching() {
        val registeredKey = ValueKey("billing")
        val equalLookupKey = ValueKey("billing")
        assertNotSame(registeredKey, equalLookupKey)
        assertEquals(registeredKey, equalLookupKey)

        val contextCreations = AtomicInteger()
        val provider = TrackingProvider()
        val manager = DatabaseManager(
            { database(registeredKey) { config = config("billing") } },
            { name, databaseConfig ->
                contextCreations.incrementAndGet()
                DatabaseContext(name, databaseConfig, provider)
            },
        )

        assertSame(registeredKey, manager[equalLookupKey].key)
        assertSame(registeredKey, manager.registeredKeys().single())
        assertEquals(ExecuteResult.Success("registered"), manager[registeredKey].connection {
            ExecuteResult.Success("registered")
        })
        assertEquals(ExecuteResult.Success("equal lookup"), manager[equalLookupKey].connection {
            ExecuteResult.Success("equal lookup")
        })
        assertEquals(1, contextCreations.get())
        assertEquals(2, provider.openCount.get())

        manager.close()
    }

    @Test
    fun distinctKeysMayShareEqualConfigurationsAndKeepIndependentContexts() {
        val sameConfig = config("shared")

        val providers = listOf(TrackingProvider(), TrackingProvider())
        val contextCreations = AtomicInteger()
        val manager = DatabaseManager(
            {
                database(Primary) { config = sameConfig }
                database(Analytics) { config = sameConfig }
            },
            { name, databaseConfig ->
                val index = contextCreations.getAndIncrement()
                DatabaseContext(name, databaseConfig, providers[index])
            },
        )

        assertEquals(listOf(Primary, Analytics), manager.registeredKeys().toList())
        assertEquals(ExecuteResult.Success("primary"), manager[Primary].connection {
            ExecuteResult.Success("primary")
        })
        assertEquals(ExecuteResult.Success("analytics"), manager[Analytics].connection {
            ExecuteResult.Success("analytics")
        })
        assertEquals(2, contextCreations.get())
        assertEquals(listOf(1, 1), providers.map { it.openCount.get() })

        manager.shutdown()
        assertEquals(listOf(1, 1), providers.map { it.closeCount.get() })
    }

    @Test
    fun dataClassKeysWithEqualValuesAreDuplicates() {
        val first = ValueKey("orders")
        val equalButDistinct = ValueKey("orders")
        assertNotSame(first, equalButDistinct)
        assertEquals(first, equalButDistinct)

        val duplicate = assertFailsWith<IllegalArgumentException> {
            DatabaseManager {
                database(first) { config = config("first") }
                database(equalButDistinct) { config = config("second") }
            }
        }
        assertTrue(duplicate.message.orEmpty().contains("ValueKey(name=orders)"))
    }

    @Test
    fun ordinaryClassesUseTheirEqualsAndHashCodeImplementation() {
        val first = OrdinaryKey("main")
        val second = OrdinaryKey("main")
        assertNotEquals(first, second) // Any's default equality is reference identity.

        val manager = DatabaseManager {
            database(first) { config = config("first") }
            database(second) { config = config("second") }
        }

        assertEquals(listOf(first, second), manager.registeredKeys().toList())
        assertEquals(first, manager[first].key)
        assertEquals(second, manager[second].key)
        manager.close()
    }

    @Test
    fun eachRegistrationKeepsItsOwnConfigurationAndIndependentContext() {
        val primaryProvider = TrackingProvider()
        val analyticsProvider = TrackingProvider()
        val manager = DatabaseManager(
            {
                database(Primary) { config = config("primary") }
                database(Analytics) { config = config("analytics") }
            },
            { name, databaseConfig ->
                val provider = when (databaseConfig.connection.dbName) {
                    "primary" -> primaryProvider
                    "analytics" -> analyticsProvider
                    else -> error("Unexpected database registration")
                }
                DatabaseContext(name, databaseConfig, provider)
            },
        )

        assertEquals(ExecuteResult.Success("primary"), manager[Primary].connection {
            ExecuteResult.Success("primary")
        })
        assertEquals(ExecuteResult.Success("analytics"), manager[Analytics].connection {
            ExecuteResult.Success("analytics")
        })
        assertEquals(1, primaryProvider.openCount.get())
        assertEquals(1, analyticsProvider.openCount.get())

        assertEquals(1, primaryProvider.openCount.get())
        assertEquals(1, analyticsProvider.openCount.get())

        manager.shutdown()
        manager.shutdown()
    }

    @Test
    fun handlesRejectNewWorkAfterIdempotentShutdown() {
        val provider = TrackingProvider()
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig -> DatabaseContext(name, databaseConfig, provider) },
        )
        val handle = manager[Primary]

        assertEquals(ExecuteResult.Success(Unit), handle.transaction { ExecuteResult.Success(Unit) })
        assertEquals(1, provider.openCount.get())
        manager.shutdown()
        manager.shutdown()

        val afterShutdown = handle.connection { ExecuteResult.Success(Unit) }
        assertIs<ExecuteResult.Failure>(afterShutdown)
        assertEquals(1, provider.openCount.get())
        assertEquals(1, provider.connectionCloseCount.get())
        assertEquals(1, provider.closeCount.get())
    }

    @Test
    fun concurrentFirstUseCreatesOnlyOneContextForARegistration() {
        val contextCreations = AtomicInteger()
        val provider = TrackingProvider()
        val factoryEntered = CountDownLatch(1)
        val allowFactoryToFinish = CountDownLatch(1)
        val callersReady = CountDownLatch(2)
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig ->
                contextCreations.incrementAndGet()
                factoryEntered.countDown()
                check(allowFactoryToFinish.await(5, TimeUnit.SECONDS))
                DatabaseContext(name, databaseConfig, provider)
            },
        )
        val handle = manager[Primary]
        val workers = Executors.newFixedThreadPool(2)

        try {
            val results = (1..2).map {
                workers.submit<ExecuteResult<Int>> {
                    callersReady.countDown()
                    check(callersReady.await(5, TimeUnit.SECONDS))
                    handle.connection { ExecuteResult.Success(1) }
                }
            }
            assertEquals(true, factoryEntered.await(5, TimeUnit.SECONDS))
            assertEquals(true, callersReady.await(5, TimeUnit.SECONDS))
            allowFactoryToFinish.countDown()
            results.forEach { assertEquals(ExecuteResult.Success(1), it.get(5, TimeUnit.SECONDS)) }

            assertEquals(1, contextCreations.get())
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            allowFactoryToFinish.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun independentRegistrationsCanInitializeConcurrently() {
        val providerByDatabase = mapOf("primary" to TrackingProvider(), "analytics" to TrackingProvider())
        val factoriesEntered = CountDownLatch(2)
        val allowFactoriesToFinish = CountDownLatch(1)
        val callersReady = CountDownLatch(2)
        val manager = DatabaseManager(
            {
                database(Primary) { config = config("primary") }
                database(Analytics) { config = config("analytics") }
            },
            { name, databaseConfig ->
                factoriesEntered.countDown()
                check(allowFactoriesToFinish.await(5, TimeUnit.SECONDS))
                DatabaseContext(name, databaseConfig, requireNotNull(providerByDatabase[databaseConfig.connection.dbName]))
            },
        )
        val workers = Executors.newFixedThreadPool(2)

        try {
            val primary = manager[Primary]
            val analytics = manager[Analytics]
            val first = workers.submit<ExecuteResult<String>> {
                callersReady.countDown()
                check(callersReady.await(5, TimeUnit.SECONDS))
                primary.connection { ExecuteResult.Success("primary") }
            }
            val second = workers.submit<ExecuteResult<String>> {
                callersReady.countDown()
                check(callersReady.await(5, TimeUnit.SECONDS))
                analytics.connection { ExecuteResult.Success("analytics") }
            }

            assertEquals(true, factoriesEntered.await(5, TimeUnit.SECONDS))
            allowFactoriesToFinish.countDown()
            assertEquals(ExecuteResult.Success("primary"), first.get(5, TimeUnit.SECONDS))
            assertEquals(ExecuteResult.Success("analytics"), second.get(5, TimeUnit.SECONDS))
        } finally {
            allowFactoriesToFinish.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun failedContextInitializationIsNotCachedAndCanBeRetried() {
        val attempts = AtomicInteger()
        val provider = TrackingProvider()
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig ->
                if (attempts.incrementAndGet() == 1) error("synthetic context initialization failure")
                DatabaseContext(name, databaseConfig, provider)
            },
        )
        val handle = manager[Primary]

        val failed = handle.connection { ExecuteResult.Success("unreachable") }
        assertIs<ExecuteResult.Failure>(failed)
        assertEquals(0, provider.openCount.get())

        assertEquals(ExecuteResult.Success("retried"), handle.connection {
            ExecuteResult.Success("retried")
        })
        assertEquals(2, attempts.get())
        assertEquals(1, provider.openCount.get())
        manager.close()
    }

    private fun config(name: String) = DatabaseConfig(
        connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, name, "user", "password"),
    )

    private class TrackingProvider : IDatabaseConnection, AutoCloseable {
        val openCount = AtomicInteger()
        val connectionCloseCount = AtomicInteger()
        val closeCount = AtomicInteger()
        val commitCount = AtomicInteger()
        val preparedStatementCloseCount = AtomicInteger()
        val executedSql = java.util.concurrent.CopyOnWriteArrayList<String>()

        override fun build(): Connection {
            openCount.incrementAndGet()
            var closed = false
            return Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "close" -> {
                        if (!closed) {
                            closed = true
                            connectionCloseCount.incrementAndGet()
                        }
                        null
                    }
                    "isClosed" -> closed
                    "getAutoCommit" -> true
                    "setAutoCommit", "rollback" -> null
                    "commit" -> {
                        commitCount.incrementAndGet()
                        null
                    }
                    "prepareStatement" -> preparedStatement(requireNotNull(args?.firstOrNull() as? String))
                    "toString" -> "TrackingConnection"
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
                "execute" -> {
                    executedSql += sql
                    true
                }
                "close" -> {
                    preparedStatementCloseCount.incrementAndGet()
                    null
                }
                "toString" -> "TrackingPreparedStatement"
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        override fun close() {
            closeCount.incrementAndGet()
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
}
