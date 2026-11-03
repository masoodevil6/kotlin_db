package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationExecutionContextTest {
    @Test
    fun `opaque context executes on the existing connection and never owns its lifecycle`() {
        var preparedSql: String? = null
        var prepareCount = 0
        var statementCloseCount = 0
        var connectionCloseCount = 0
        val boundValues = mutableListOf<List<Any?>>()

        val statement = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            when (method.name) {
                "execute" -> true
                "setObject" -> { boundValues += args!!.toList(); null }
                "close" -> { statementCloseCount++; null }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        val connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> { prepareCount++; preparedSql = args!![0] as String; statement }
                "close" -> { connectionCloseCount++; null }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as Connection

        val scope = OperationExecutionScope(connection)
        val context: OperationExecutionContext = scope
        val result = context.executeDdl(
            "ALTER TABLE customers ADD COLUMN note VARCHAR(12) DEFAULT :note",
            listOf(OperationParameter("note", "example", Types.VARCHAR)),
        )

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals("ALTER TABLE customers ADD COLUMN note VARCHAR(12) DEFAULT ?", preparedSql)
        assertEquals(1, boundValues.size)
        assertEquals(1, boundValues.single()[0])
        assertEquals("example", boundValues.single()[1])
        assertEquals(Types.VARCHAR, boundValues.single()[2])
        assertEquals(1, prepareCount)
        assertEquals(1, statementCloseCount)
        assertEquals(0, connectionCloseCount)

        val primaryFailure = IllegalStateException("already failed")
        val failed = context.failed<Boolean>(primaryFailure)
        assertSame(primaryFailure, assertIs<ExecuteResult.Failure>(failed).exception)
        val afterFailure = context.executeDdl("ALTER TABLE customers DROP COLUMN note")
        assertSame(primaryFailure, assertIs<ExecuteResult.Failure>(afterFailure).exception)
        assertEquals(1, prepareCount)

        scope.end()
        val afterEnd = context.executeDdl("ALTER TABLE customers DROP COLUMN note")
        assertIs<ExecuteResult.Failure>(afterEnd)
        assertEquals(1, prepareCount)
        assertEquals(0, connectionCloseCount)
    }

    @Test
    fun `opaque execution context does not expose JDBC types`() {
        val exposedTypes = OperationExecutionContext::class.java.methods
            .flatMap { method -> listOf(method.returnType) + method.parameterTypes }
        assertFalse(exposedTypes.any { Connection::class.java.isAssignableFrom(it) })
        assertFalse(exposedTypes.any { it.name.startsWith("java.sql.") })
        assertTrue(OperationExecutionContext::class.java.methods.none { it.name == "getConnection" })
    }

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
