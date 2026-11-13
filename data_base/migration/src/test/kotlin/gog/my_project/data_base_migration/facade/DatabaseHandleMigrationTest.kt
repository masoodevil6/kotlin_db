package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseHandleApi
import gog.my_project.data_base_manager.facade.DatabaseHandleAttributes
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.DatabaseRegistrationAttribute
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import model.migration_status.MigrationStatusReport
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.util.Properties
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseHandleMigrationTest {
    @Test
    fun `no-configuration migration uses the configuration registered for its own handle`() =
        withHandle(attachedConfiguration = configuration("MainVersion")) { handle, driver ->
            val result = handle.migration {
                val statusResult = status()
                val report = assertIs<ExecuteResult.Success<MigrationStatusReport>>(statusResult).result
                assertEquals(listOf("MainVersion"), report?.versions?.map { it.name })
                assertEquals(1, driver.connections.single().queryCount)
                assertFalse(driver.connections.single().closed)

                val currentResult = current()
                assertIs<ExecuteResult.Success<MigrationVersion?>>(currentResult)
                assertEquals(4, driver.connections.single().queryCount)
                assertFalse(driver.connections.single().closed)

                "registered configuration"
            }

            assertEquals("registered configuration", assertIs<ExecuteResult.Success<String>>(result).result)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
            assertEquals(0, driver.connections.single().commitCount)
            assertEquals(0, driver.connections.single().rollbackCount)
            assertEquals(0, driver.connections.single().autoCommitChanges)
        }

    @Test
    fun `registration-bound facade supports init then migrate on one managed scope`() =
        withHandle(attachedConfiguration = configuration("MainVersion")) { handle, driver ->
            var initResult: ExecuteResult<Boolean>? = null
            var migrateResult: ExecuteResult<Boolean>? = null

            val result = handle.migration {
                initResult = init()
                assertEquals(3, driver.connections.single().ddlCount)

                // This fixture intentionally uses a non-canonical JDBC URL, so Migration's
                // required cross-process SystemLock fails closed before it can read or mutate.
                migrateResult = migrate()
                assertEquals(3, driver.connections.single().ddlCount)
                "init then migrate"
            }

            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(initResult).result)
            val migrationFailure = assertIs<ExecuteResult.Failure>(migrateResult).exception
            assertIs<IllegalStateException>(migrationFailure)
            assertEquals("A canonical DatabaseIdentity is required to run a system lock", migrationFailure.message)
            assertSame(migrationFailure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(1, driver.openCount)
            assertEquals(3, driver.connections.single().ddlCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `registration configurations remain independent between handles`() {
        val driver = TrackingDriver()
        DriverManager.registerDriver(driver)
        val manager = DatabaseManager {
            database(TestDatabase) {
                config = databaseConfig()
                migrationConfiguration = configuration("MainVersion")
            }
            database(AnalyticsDatabase) {
                config = databaseConfig()
                migrationConfiguration = configuration("AnalyticsVersion")
            }
        }

        try {
            val mainResult = manager[TestDatabase].migration { status() }
            val analyticsResult = manager[AnalyticsDatabase].migration { status() }

            val main = assertIs<ExecuteResult.Success<ExecuteResult.Success<MigrationStatusReport>>>(
                mainResult,
            ).result?.result
            val analytics = assertIs<ExecuteResult.Success<ExecuteResult.Success<MigrationStatusReport>>>(
                analyticsResult,
            ).result?.result
            assertEquals(listOf("MainVersion"), main?.versions?.map { it.name })
            assertEquals(listOf("AnalyticsVersion"), analytics?.versions?.map { it.name })
            assertEquals(2, driver.openCount)
            assertEquals(2, driver.connections.size)
            assertTrue(driver.connections.all { it.closeCount == 1 && it.queryCount == 1 })
        } finally {
            try {
                manager.close()
            } finally {
                DriverManager.deregisterDriver(driver)
            }
        }
    }

    @Test
    fun `all commands are available through registration-bound overload`() =
        withHandle(
            TrackingDriver(queryFailure = SQLException("test query failure")),
            attachedConfiguration = configuration("MainVersion"),
        ) { handle, driver ->
            val versionTag = object : MigrationVersionTag {
                override val value: String = "unknown"
            }
            var commandResults: List<ExecuteResult<*>>? = null

            val result = handle.migration {
                commandResults = listOf(
                    init(),
                    status(),
                    migrate(),
                    migrate(versionTag),
                    rollback(),
                    rollback(versionTag),
                    current(),
                )
                commandResults!!
            }

            val failure = assertIs<ExecuteResult.Failure>(result)
            val sqlFailure = assertIs<SQLException>(failure.exception)
            val commands = requireNotNull(commandResults)
            assertEquals(7, commands.size)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(commands.first()).result)
            commands.drop(1).forEach { commandResult ->
                assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(commandResult).exception)
            }
            assertEquals(3, driver.connections.single().ddlCount)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `missing registration configuration fails before invoking block or opening connection`() =
        withHandle { handle, driver ->
            var invoked = false

            val result = handle.migration {
                invoked = true
                "unreachable"
            }

            assertIs<IllegalStateException>(assertIs<ExecuteResult.Failure>(result).exception)
            assertFalse(invoked)
            assertEquals(0, driver.openCount)
        }

    @Test
    fun `handle without Registration attributes fails before invoking no-configuration block`() {
        var invoked = false

        val result = LegacyHandle().migration {
            invoked = true
            "unreachable"
        }

        assertIs<UnsupportedOperationException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertFalse(invoked)
    }

    @Test
    fun `custom handle with attributes but without managed scope fails before invoking block`() {
        var invoked = false

        val result = AttributedLegacyHandle(configuration("MainVersion")).migration {
            invoked = true
            "unreachable"
        }

        assertIs<UnsupportedOperationException>(assertIs<ExecuteResult.Failure>(result).exception)
        assertFalse(invoked)
    }

    @Test
    fun `registration-bound overload preserves nested ExecuteResult block value`() =
        withHandle(attachedConfiguration = configuration("MainVersion")) { handle, driver ->
            val blockValue = ExecuteResult.Failure(IllegalArgumentException("consumer result"))

            val result = handle.migration { blockValue }

            val outer = assertIs<ExecuteResult.Success<ExecuteResult.Failure>>(result)
            assertSame(blockValue, outer.result)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `registration-bound callback failure remains primary when cleanup also fails`() =
        withHandle(
            TrackingDriver(closeFailure = SQLException("close failure")),
            attachedConfiguration = configuration("MainVersion"),
        ) { handle, driver ->
            val callbackFailure = IllegalStateException("callback failure")

            val result = handle.migration { throw callbackFailure }

            assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals("close failure", callbackFailure.suppressed.single().message)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `registration-bound fatal Error propagates after cleanup`() =
        withHandle(
            TrackingDriver(closeFailure = SQLException("close after fatal")),
            attachedConfiguration = configuration("MainVersion"),
        ) { handle, driver ->
            val fatal = AssertionError("fatal callback")

            val thrown = assertFailsWith<AssertionError> {
                handle.migration { throw fatal }
            }

            assertSame(fatal, thrown)
            assertEquals("close after fatal", fatal.suppressed.single().message)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `handle migration uses one managed connection for immediate commands and preserves block value`() =
        withHandle { handle, driver ->
            val blockValue = ExecuteResult.Success("consumer value")

            val result = handle.migration(configuration("MainVersion")) {
                val statusResult = status()
                val report = assertIs<ExecuteResult.Success<MigrationStatusReport>>(statusResult).result
                assertEquals(listOf("MainVersion"), report?.versions?.map { it.name })
                assertEquals(1, driver.connections.single().queryCount)
                assertFalse(driver.connections.single().closed)

                val currentResult = current()
                assertIs<ExecuteResult.Success<MigrationVersion?>>(currentResult)
                assertEquals(4, driver.connections.single().queryCount)
                assertFalse(driver.connections.single().closed)

                blockValue
            }

            val outer = assertIs<ExecuteResult.Success<ExecuteResult.Success<String>>>(result)
            assertSame(blockValue, outer.result)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
            assertTrue(driver.connections.single().closed)
            assertEquals(0, driver.connections.single().commitCount)
            assertEquals(0, driver.connections.single().rollbackCount)
            assertEquals(0, driver.connections.single().autoCommitChanges)
        }

    @Test
    fun `separate invocations use their own explicit MigrationConfiguration`() = withHandle { handle, driver ->
        val first = handle.migration(configuration("MainVersion")) { status() }
        val second = handle.migration(configuration("AnalyticsVersion")) { status() }

        val firstStatus = assertIs<ExecuteResult.Success<ExecuteResult.Success<MigrationStatusReport>>>(
            first,
        ).result?.result
        val secondStatus = assertIs<ExecuteResult.Success<ExecuteResult.Success<MigrationStatusReport>>>(
            second,
        ).result?.result

        assertEquals(listOf("MainVersion"), firstStatus?.versions?.map { it.name })
        assertEquals(listOf("AnalyticsVersion"), secondStatus?.versions?.map { it.name })
        assertEquals(2, driver.openCount)
        assertEquals(2, driver.connections.size)
        assertTrue(driver.connections.all { it.closeCount == 1 && it.queryCount == 1 })
    }

    @Test
    fun `ExecuteResult returned by block remains nested inside the Operation success`() =
        withHandle { handle, driver ->
            val blockValue = ExecuteResult.Failure(IllegalArgumentException("consumer result"))

            val result = handle.migration(configuration("MainVersion")) { blockValue }

            val outer = assertIs<ExecuteResult.Success<ExecuteResult.Failure>>(result)
            assertSame(blockValue, outer.result)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `all commands use the managed scope and an Operation query failure is preserved`() =
        withHandle(TrackingDriver(queryFailure = SQLException("test query failure"))) { handle, driver ->
            val versionTag = object : MigrationVersionTag {
                override val value: String = "unknown"
            }
            var commandResults: List<ExecuteResult<*>>? = null

            val result = handle.migration(configuration("MainVersion")) {
                commandResults = listOf(
                    init(),
                    status(),
                    migrate(),
                    migrate(versionTag),
                    rollback(),
                    rollback(versionTag),
                    current(),
                )
                commandResults!!
            }

            val failure = assertIs<ExecuteResult.Failure>(result)
            val sqlFailure = assertIs<SQLException>(failure.exception)
            val commands = requireNotNull(commandResults)
            assertEquals(7, commands.size)
            assertEquals(true, assertIs<ExecuteResult.Success<Boolean>>(commands.first()).result)
            commands.drop(1).forEach { commandResult ->
                assertSame(sqlFailure, assertIs<ExecuteResult.Failure>(commandResult).exception)
            }
            assertEquals(3, driver.connections.single().ddlCount)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `ordinary callback exception stays primary when Operation cleanup fails`() =
        withHandle(TrackingDriver(closeFailure = SQLException("close failure"))) { handle, driver ->
            val callbackFailure = IllegalStateException("callback failure")

            val result = handle.migration(configuration("MainVersion")) { throw callbackFailure }

            assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals("close failure", callbackFailure.suppressed.single().message)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `fatal callback Error propagates after Operation cleanup`() =
        withHandle(TrackingDriver(closeFailure = SQLException("close after fatal"))) { handle, driver ->
            val fatal = AssertionError("fatal callback")

            val thrown = assertFailsWith<AssertionError> {
                handle.migration(configuration("MainVersion")) { throw fatal }
            }

            assertSame(fatal, thrown)
            assertEquals("close after fatal", fatal.suppressed.single().message)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `successful block value is not reported as success if Operation cleanup fails`() =
        withHandle(TrackingDriver(closeFailure = SQLException("close failure"))) { handle, driver ->
            val result = handle.migration(configuration("MainVersion")) { "completed" }

            val failure = assertIs<ExecuteResult.Failure>(result)
            assertIs<IllegalStateException>(failure.exception)
            assertIs<SQLException>(failure.exception.cause)
            assertEquals(1, driver.openCount)
            assertEquals(1, driver.connections.single().closeCount)
        }

    @Test
    fun `custom DatabaseHandleApi without managed scope fails without invoking block`() {
        var invoked = false
        val handle = LegacyHandle()

        val result = handle.migration(configuration("MainVersion")) {
            invoked = true
            "unreachable"
        }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<UnsupportedOperationException>(failure.exception)
        assertFalse(invoked)
    }

    private fun configuration(name: String) = MigrationConfiguration(
        listOf(MigrationVersion(name, emptyList())),
    )

    private fun <T> withHandle(
        driver: TrackingDriver = TrackingDriver(),
        attachedConfiguration: MigrationConfiguration? = null,
        block: (DatabaseHandleApi, TrackingDriver) -> T,
    ): T {
        DriverManager.registerDriver(driver)
        val manager = DatabaseManager {
            database(TestDatabase) {
                config = DatabaseConfig(
                    dbDomain = "jdbc:migration-facade-test://localhost",
                    dbPort = null,
                    dbName = "test_catalog",
                    dbUserName = "test_user",
                    dbPassword = "test_password",
                    dialect = DialectQuery.MY_SQL,
                )
                attachedConfiguration?.let { migrationConfiguration = it }
            }
        }
        try {
            return block(manager[TestDatabase], driver)
        } finally {
            try {
                manager.close()
            } finally {
                DriverManager.deregisterDriver(driver)
            }
        }
    }

    private object TestDatabase : DatabaseKey
    private object AnalyticsDatabase : DatabaseKey

    private fun databaseConfig() = DatabaseConfig(
        dbDomain = "jdbc:migration-facade-test://localhost",
        dbPort = null,
        dbName = "test_catalog",
        dbUserName = "test_user",
        dbPassword = "test_password",
        dialect = DialectQuery.MY_SQL,
    )

    private class LegacyHandle : DatabaseHandleApi {
        override val key: DatabaseKey = TestDatabase

        override fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            error("The unsupported scope extension must not call connection()")

        override fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            error("The unsupported scope extension must not call transaction()")
    }

    private class AttributedLegacyHandle(
        private val configuration: MigrationConfiguration,
    ) : DatabaseHandleApi, DatabaseHandleAttributes {
        override val key: DatabaseKey = TestDatabase

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> attribute(key: DatabaseRegistrationAttribute<T>): T? =
            if (key === MigrationConfigurationRegistration.key) configuration as T else null

        override fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            error("The unsupported scope extension must not call connection()")

        override fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            error("The unsupported scope extension must not call transaction()")
    }

    private class TrackingDriver(
        private val queryFailure: Throwable? = null,
        private val closeFailure: SQLException? = null,
        private val ddlFailure: Throwable? = null,
    ) : Driver {
        val connections = mutableListOf<ConnectionState>()
        val openCount: Int get() = connections.size

        override fun connect(url: String?, info: Properties?): Connection? {
            if (!acceptsURL(url)) return null
            return ConnectionState(this).also { connections += it }.connection
        }

        override fun acceptsURL(url: String?): Boolean = url?.startsWith("jdbc:migration-facade-test:") == true

        override fun getPropertyInfo(url: String?, info: Properties?): Array<DriverPropertyInfo> = emptyArray()

        override fun getMajorVersion(): Int = 1

        override fun getMinorVersion(): Int = 0

        override fun jdbcCompliant(): Boolean = false

        override fun getParentLogger(): Logger = Logger.getLogger("migration-facade-test")

        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }

        inner class ConnectionState(private val driver: TrackingDriver) {
            var queryCount = 0
            var ddlCount = 0
            var closeCount = 0
            var commitCount = 0
            var rollbackCount = 0
            var autoCommitChanges = 0
            var closed = false
                private set

            val connection: Connection = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "prepareStatement" -> statement(args?.firstOrNull() as? String ?: "")
                    "getAutoCommit" -> true
                    "setAutoCommit" -> { autoCommitChanges++; null }
                    "commit" -> { commitCount++; null }
                    "rollback" -> { rollbackCount++; null }
                    "close" -> {
                        closeCount++
                        closed = true
                        driver.closeFailure?.let { throw it }
                        null
                    }
                    "isClosed" -> closed
                    "toString" -> "MigrationFacadeTestConnection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> defaultValue(method.returnType)
                }
            } as Connection

            private fun statement(sql: String): PreparedStatement = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "executeQuery" -> {
                        queryCount++
                        driver.queryFailure?.let { throw it }
                        resultSet()
                    }
                    "execute" -> {
                        ddlCount++
                        driver.ddlFailure?.let { throw it }
                        true
                    }
                    "close" -> null
                    "isClosed" -> false
                    "toString" -> sql
                    else -> defaultValue(method.returnType)
                }
            } as PreparedStatement

            private fun resultSet(): ResultSet = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "next" -> false
                    "getMetaData" -> metadata()
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet

            private fun metadata(): ResultSetMetaData = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getColumnCount" -> 0
                    else -> defaultValue(method.returnType)
                }
            } as ResultSetMetaData
        }
    }

}
