package gog.my_project.data_base_manager.facade

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.api.interfaces.MigrationConfiguration
import gog.my_project.data_base_schema.Schema
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertFailsWith

class DatabaseCatalogTest {
    private object Primary : DatabaseKey
    private object Analytics : DatabaseKey
    private object Unregistered : DatabaseKey

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

        val result = first.query { ExecuteResult.Success("done") }

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
    fun duplicateAndUnknownKeysAreRejectedWithoutCreatingContexts() {
        assertFailsWith<IllegalArgumentException> {
            DatabaseManager {
                database(Primary) { config = config("primary") }
                database(Primary) { config = config("other") }
            }
        }

        val manager = DatabaseManager()
        assertFailsWith<NoSuchElementException> { manager.get(Unregistered) }
        manager.close()
    }

    @Test
    fun eachRegistrationKeepsItsOwnConfigurationAndMigrationAvailability() {
        val primaryProvider = TrackingProvider()
        val analyticsProvider = TrackingProvider()
        val manager = DatabaseManager(
            {
                database(Primary) { config = config("primary") }
                database(Analytics) {
                    config = config("analytics")
                    migrations = MigrationConfiguration(emptyList())
                }
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

        assertEquals(ExecuteResult.Success("primary"), manager[Primary].query {
            ExecuteResult.Success("primary")
        })
        assertEquals(ExecuteResult.Success("analytics"), manager[Analytics].query {
            ExecuteResult.Success("analytics")
        })
        assertEquals(1, primaryProvider.openCount.get())
        assertEquals(1, analyticsProvider.openCount.get())

        var primaryMigrationResult: ExecuteResult<Unit>? = null
        manager[Primary].migrate { primaryMigrationResult = it }
        assertIs<ExecuteResult.Failure>(primaryMigrationResult)
        assertEquals(ExecuteResult.Success(Unit), runMigration(manager[Analytics]))
        assertEquals(1, primaryProvider.openCount.get()) // missing migration configuration opens no additional connection.
        assertEquals(1, analyticsProvider.openCount.get()) // empty migration list completes without opening another connection.

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

        val afterShutdown = handle.query { ExecuteResult.Success(Unit) }
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
                    handle.query { ExecuteResult.Success(1) }
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
                primary.query { ExecuteResult.Success("primary") }
            }
            val second = workers.submit<ExecuteResult<String>> {
                callersReady.countDown()
                check(callersReady.await(5, TimeUnit.SECONDS))
                analytics.query { ExecuteResult.Success("analytics") }
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

        val failed = handle.query { ExecuteResult.Success("unreachable") }
        assertIs<ExecuteResult.Failure>(failed)
        assertEquals(0, provider.openCount.get())

        assertEquals(ExecuteResult.Success("retried"), handle.query {
            ExecuteResult.Success("retried")
        })
        assertEquals(2, attempts.get())
        assertEquals(1, provider.openCount.get())
        manager.close()
    }

    @Test
    fun schemaHandleUsesThePublicSchemaFacadeAndSelectedDatabase() {
        val provider = TrackingProvider()
        val manager = DatabaseManager(
            { database(Primary) { config = config("primary") } },
            { name, databaseConfig -> DatabaseContext(name, databaseConfig, provider) },
        )
        val operation = Schema.dropTable {
            tableName("isolated_example_table")
            ifExists()
        }
        val queryInfo = mutableListOf<Pair<String, Map<String, Any?>>>()

        val result = manager[Primary].schema(operation) { sql, parameters ->
            queryInfo += sql to parameters
        }

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(1, provider.openCount.get())
        assertEquals(1, provider.connectionCloseCount.get())
        assertEquals(1, provider.preparedStatementCloseCount.get())
        assertEquals(1, provider.executedSql.size)
        assertEquals(provider.executedSql.single(), queryInfo.single().first)
        assertEquals(emptyMap(), queryInfo.single().second)
        manager.close()
    }

    private fun runMigration(handle: DatabaseHandle): ExecuteResult<Unit> {
        var result: ExecuteResult<Unit>? = null
        handle.migrate { result = it }
        return requireNotNull(result)
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
