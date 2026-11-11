package gog.my_project.data_base_migration.executor.service.run

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.facade.DatabaseKey
import gog.my_project.data_base_manager.facade.DatabaseManager
import gog.my_project.data_base_manager.facade.withOperationScope
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_migration.executor.controller.MigrationController
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_migration.executor.support.systemMigrationResultSetMetadata
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Properties
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationManagerSystemLockIntegrationTest {
    @Test
    fun `all Manager orchestration entry points use one locked root scope through cleanup`() {
        val driver = MigrationLockDriver()
        DriverManager.registerDriver(driver)
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://migration-lock-test.local",
                dbPort = 3306,
                dbName = "migration_lock_test",
                dbUserName = "test-user",
                dbPassword = "test-password",
            ),
        )
        val manager = DatabaseManager { database(TestDatabase) { this.config = config } }
        val controller = MigrationController(MigrationConfiguration(emptyList()))
        val target = object : MigrationVersionTag { override val value = "unknown_target" }

        try {
            val handle = manager[TestDatabase]
            val commands: List<(OperationExecutionScope) -> ExecuteResult<Boolean>> = listOf(
                controller::migrate,
                { scope -> controller.migrate(target, scope) },
                controller::rollback,
                { scope -> controller.rollback(target, scope) },
            )

            val results = commands.map { command ->
                handle.withOperationScope { scope ->
                    assertNotNull(scope.databaseIdentity)
                    assertSame(driver.scripts.last().connection, scope.connection)
                    command(scope)
                }
            }

            assertIs<ExecuteResult.Success<Boolean>>(results[0])
            assertIs<ExecuteResult.Failure>(results[1]) // The requested target is absent from this empty catalog.
            assertIs<ExecuteResult.Success<Boolean>>(results[2])
            assertIs<ExecuteResult.Failure>(results[3])

            assertEquals(4, driver.scripts.size)
            driver.scripts.forEach { script ->
                assertEquals(1, script.events.count { it == "acquire" })
                assertEquals(3, script.events.count { it == "history-read" })
                assertEquals(1, script.events.count { it == "release" })
                assertEquals(1, script.events.count { it == "connection-close" })
                assertTrue(script.events.indexOf("acquire") < script.events.indexOf("history-read"))
                assertTrue(script.events.lastIndexOf("history-read") < script.events.indexOf("release"))
                assertTrue(script.events.indexOf("release") < script.events.indexOf("connection-close"))
                assertEquals(0, script.events.count { it == "commit" || it == "rollback" })
            }
        } finally {
            manager.close()
            DriverManager.deregisterDriver(driver)
        }
    }

    private object TestDatabase : DatabaseKey

    private class MigrationLockDriver : Driver {
        val scripts = mutableListOf<ConnectionScript>()

        override fun connect(url: String, info: Properties?): Connection? {
            if (!acceptsURL(url)) return null
            return ConnectionScript().also(scripts::add).connection
        }

        override fun acceptsURL(url: String): Boolean =
            url == "jdbc:mysql://migration-lock-test.local:3306/migration_lock_test"

        override fun getPropertyInfo(url: String?, info: Properties?) = emptyArray<DriverPropertyInfo>()
        override fun getMajorVersion() = 1
        override fun getMinorVersion() = 0
        override fun jdbcCompliant() = false
        override fun getParentLogger(): Logger = Logger.getLogger("migration-lock-test")
    }

    private class ConnectionScript {
        val events = mutableListOf<String>()
        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> statement(args?.firstOrNull()?.toString().orEmpty())
                "getAutoCommit" -> true
                "close" -> { events += "connection-close"; null }
                "isClosed" -> false
                "commit" -> { events += "commit"; null }
                "rollback" -> { events += "rollback"; null }
                else -> primitiveDefault(method.returnType)
            }
        }

        private fun statement(sql: String): PreparedStatement = proxy(PreparedStatement::class.java) { method, _ ->
            when (method.name) {
                "executeQuery" -> {
                    val lower = sql.lowercase()
                    val row = when {
                        "get_lock(" in lower -> {
                            events += "acquire"
                            mapOf("acquired" to 1)
                        }
                        "release_lock(" in lower -> {
                            events += "release"
                            mapOf("released" to 1)
                        }
                        else -> {
                            events += "history-read"
                            null
                        }
                    }
                    resultSet(sql, listOfNotNull(row))
                }
                "close" -> null
                "isClosed" -> false
                else -> primitiveDefault(method.returnType)
            }
        }

        private fun resultSet(sql: String, rows: List<Map<String, Any?>>): ResultSet {
            var index = -1
            var lastWasNull = false
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "getMetaData" -> systemMigrationResultSetMetadata(sql)
                    "next" -> ++index < rows.size
                    "getInt" -> {
                        val value = rows[index][args?.firstOrNull()?.toString().orEmpty()]
                        lastWasNull = value == null
                        (value as? Number)?.toInt() ?: 0
                    }
                    "wasNull" -> lastWasNull
                    "close" -> null
                    "isClosed" -> false
                    else -> primitiveDefault(method.returnType)
                }
            }
        }

        private fun <T> proxy(type: Class<T>, action: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): T =
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args -> action(method, args) } as T

        private fun primitiveDefault(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
