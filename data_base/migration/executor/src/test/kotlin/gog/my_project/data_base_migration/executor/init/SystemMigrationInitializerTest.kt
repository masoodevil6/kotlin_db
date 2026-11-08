package gog.my_project.data_base_migration.executor.init

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationTable
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationTagTable
import gog.my_project.data_base_migration.core.system_migration.schema.SystemMigrationVersionTable
import gog.my_project.data_base_migration.executor.service.init.SystemMigrationInitializer
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemMigrationInitializerTest {
    @Test
    fun `init creates all tables in order on the supplied scope without owning connection lifecycle`() {
        val events = mutableListOf<String>()
        val statements = mutableListOf<String>()
        val statementConnections = mutableListOf<Connection>()
        lateinit var connection: Connection
        connection = fakeConnection(events, statements, statementConnections)
        val initializer = SystemMigrationInitializer()

        val result = initializer.init(testScope(connection))

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(3, statements.size)
        assertTrue(statements[0].contains(SystemMigrationTagTable.NAME))
        assertTrue(statements[1].contains(SystemMigrationVersionTable.NAME))
        assertTrue(statements[2].contains(SystemMigrationTable.NAME))
        assertTrue(statementConnections.all { it === connection })
        assertEquals(0, events.count { it.startsWith("autoCommit:") })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    @Test
    fun `version table failure stops before migration table and leaves lifecycle to caller`() {
        val events = mutableListOf<String>()
        val statements = mutableListOf<String>()
        val statementConnections = mutableListOf<Connection>()
        val originalFailure = SQLException("version table creation failed")
        lateinit var connection: Connection
        connection = fakeConnection(
            events,
            statements,
            statementConnections,
            failWhenSqlContains = tableReference(SystemMigrationVersionTable.NAME),
            failure = originalFailure,
        )
        val initializer = SystemMigrationInitializer()

        val scope = testScope(connection)
        val result = initializer.init(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(originalFailure, scope.firstFailure)
        assertEquals(2, statements.size)
        assertTrue(statements.none { it.contains(tableReference(SystemMigrationTable.NAME)) })
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    @Test
    fun `migration table failure is latched without finalizing caller transaction`() {
        val events = mutableListOf<String>()
        val statements = mutableListOf<String>()
        val statementConnections = mutableListOf<Connection>()
        val originalFailure = SQLException("migration table creation failed")
        lateinit var connection: Connection
        connection = fakeConnection(
            events,
            statements,
            statementConnections,
            failWhenSqlContains = tableReference(SystemMigrationTable.NAME),
            failure = originalFailure,
        )
        val initializer = SystemMigrationInitializer()

        val scope = testScope(connection)
        val result = initializer.init(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(originalFailure, scope.firstFailure)
        assertEquals(3, statements.size)
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    @Test
    fun `repeated init uses if not exists definitions on caller scope`() {
        val events = mutableListOf<String>()
        val statements = mutableListOf<String>()
        val statementConnections = mutableListOf<Connection>()
        val connection = fakeConnection(events, statements, statementConnections)
        val scope = testScope(connection)
        val initializer = SystemMigrationInitializer()

        assertEquals(ExecuteResult.Success(true), initializer.init(scope))
        assertEquals(ExecuteResult.Success(true), initializer.init(scope))

        assertEquals(6, statements.size)
        assertTrue(statements.all { it.contains("IF NOT EXISTS", ignoreCase = true) })
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    private fun testScope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))

    private fun tableReference(name: String): String = "`$name`"

    private fun fakeConnection(
        events: MutableList<String>,
        statements: MutableList<String>,
        statementConnections: MutableList<Connection>,
        failWhenSqlContains: String? = null,
        failure: SQLException? = null,
    ): Connection {
        lateinit var connection: Connection
        connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "setAutoCommit" -> {
                    events += "autoCommit:${args!![0]}"
                    null
                }
                "prepareStatement" -> {
                    val sql = args!![0] as String
                    statements += sql
                    statementConnections += connection
                    fakeStatement(events, sql, failWhenSqlContains, failure)
                }
                "commit" -> {
                    events += "commit"
                    null
                }
                "rollback" -> {
                    events += "rollback"
                    null
                }
                "close" -> {
                    events += "connection-close"
                    null
                }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as Connection
        return connection
    }

    private fun fakeStatement(
        events: MutableList<String>,
        sql: String,
        failWhenSqlContains: String?,
        failure: SQLException?,
    ): PreparedStatement = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "execute" -> {
                events += "execute"
                if (failWhenSqlContains != null && sql.contains(failWhenSqlContains)) {
                    throw requireNotNull(failure)
                }
                true
            }
            "close" -> {
                events += "statement-close"
                null
            }
            "isClosed" -> false
            else -> defaultValue(method.returnType)
        }
    } as PreparedStatement

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
}
