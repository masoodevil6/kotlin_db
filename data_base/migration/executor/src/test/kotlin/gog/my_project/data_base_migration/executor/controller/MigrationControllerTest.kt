package gog.my_project.data_base_migration.executor.controller

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class MigrationControllerTest {
    @Test
    fun `init returns successful initializer result`() {
        val events = mutableListOf<String>()
        val scope = testScope(connection(events))
        val controller = MigrationController()

        val result = controller.init(scope)

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    @Test
    fun `init returns initializer failure with the original cause`() {
        val events = mutableListOf<String>()
        val originalFailure = SQLException("system table initialization failed")
        val scope = testScope(connection(events, failure = originalFailure))
        val controller = MigrationController()

        val result = controller.init(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(originalFailure, scope.firstFailure)
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    private fun testScope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))

    private fun connection(
        events: MutableList<String>,
        failure: SQLException? = null,
    ): Connection = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "setAutoCommit" -> {
                events += "autoCommit:${args?.get(0)}"
                null
            }
            "prepareStatement" -> Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, statementMethod, _ ->
                when (statementMethod.name) {
                    "execute" -> {
                        if (failure != null) throw failure
                        true
                    }
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(statementMethod.returnType)
                }
            } as PreparedStatement
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
