package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun `scoped reads share the transaction connection and release query resources`() {
        val events = mutableListOf<String>()
        val boundValues = mutableListOf<List<Any?>>()
        val script = ReadScript(
            events = events,
            boundValues = boundValues,
            rows = listOf(
                "customer_id" to "customer-9",
                "record_count" to "12",
            ),
        )
        var acquisitions = 0
        var retainedRows: OperationQueryRows? = null

        val result = OperationTransactionExecutor(
            connectionFactory = { acquisitions++; script.connection },
            databasePlatformConfig = gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig(
                dialect = gog.my_project.data_base_operation.config.query.dialect.DialectQuery.MARIA_DB,
            ),
        ).transaction { scope ->
            val customer = scope.executeRead(
                "SELECT :id AS customer_id",
                listOf(OperationParameter("id", 9, Types.INTEGER)),
            ) { rows ->
                assertEquals(listOf("customer_id"), rows.columnLabels)
                assertTrue(rows.next())
                assertEquals("customer-9", rows.getString("customer_id"))
                retainedRows = rows
                assertFalse(rows.next())
                rows.getString("customer_id")
            }
            assertEquals("customer-9", assertIs<ExecuteResult.Success<String>>(customer).result)
            assertFalse(events.contains("close"))
            assertFailsWith<IllegalStateException> { retainedRows!!.next() }

            val count = scope.executeRead("SELECT 12 AS record_count") { rows ->
                assertEquals(listOf("record_count"), rows.columnLabels)
                assertTrue(rows.next())
                assertEquals(12L, rows.getLong("record_count"))
                assertFalse(rows.next())
                rows.getLong("record_count")
            }
            assertEquals(12L, assertIs<ExecuteResult.Success<Long>>(count).result)
            assertFalse(events.contains("close"))

            assertEquals(ExecuteResult.Success(true), scope.executeDdl("CREATE TABLE scoped_read_test (id INT)"))
            assertFalse(events.contains("close"))
            ExecuteResult.Success("completed")
        }

        assertEquals("completed", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(1, acquisitions)
        assertEquals(
            listOf(
                "autoCommit:false",
                "prepare:SELECT ? AS customer_id",
                "executeQuery",
                "resultSet.close:customer_id",
                "statement.close:SELECT ? AS customer_id",
                "prepare:SELECT 12 AS record_count",
                "executeQuery",
                "resultSet.close:record_count",
                "statement.close:SELECT 12 AS record_count",
                "prepare:CREATE TABLE scoped_read_test (id INT)",
                "execute",
                "statement.close:CREATE TABLE scoped_read_test (id INT)",
                "commit",
                "close",
            ),
            events,
        )
        assertEquals(
            listOf<List<Any?>>(listOf(1, 9, Types.INTEGER)),
            boundValues,
        )
    }

    @Test
    fun `scoped read failure is latched and causes transaction rollback`() {
        val events = mutableListOf<String>()
        val failure = SQLException("read failed")
        val script = ReadScript(events = events, rows = emptyList(), readFailure = failure)
        var acquisitions = 0

        val result = OperationTransactionExecutor(
            connectionFactory = { acquisitions++; script.connection },
            databasePlatformConfig = gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig(
                dialect = gog.my_project.data_base_operation.config.query.dialect.DialectQuery.MARIA_DB,
            ),
        ).transaction { scope ->
            val read = scope.executeRead("SELECT value FROM sample") { rows -> rows.next() }
            assertSame(failure, assertIs<ExecuteResult.Failure>(read).exception)

            val later = scope.executeDdl("CREATE TABLE must_not_run (id INT)")
            assertSame(failure, assertIs<ExecuteResult.Failure>(later).exception)
            ExecuteResult.Success("must rollback")
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, acquisitions)
        assertEquals(
            listOf(
                "autoCommit:false",
                "prepare:SELECT value FROM sample",
                "executeQuery",
                "statement.close:SELECT value FROM sample",
                "rollback",
                "close",
            ),
            events,
        )
    }

    @Test
    fun `scoped rows reject cross thread access and expire when read callback returns`() {
        val events = mutableListOf<String>()
        val script = ReadScript(
            events = events,
            rows = listOf("id" to "7"),
        )
        var escapedRows: OperationQueryRows? = null
        var workerFailure: Throwable? = null

        val result = OperationTransactionExecutor(
            connectionFactory = { script.connection },
            databasePlatformConfig = gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig(
                dialect = gog.my_project.data_base_operation.config.query.dialect.DialectQuery.MARIA_DB,
            ),
        ).transaction { scope ->
            val read = scope.executeRead("SELECT 7 AS id") { rows ->
                escapedRows = rows
                val worker = thread(start = true) {
                    workerFailure = try {
                        rows.next()
                        null
                    } catch (failure: Throwable) {
                        failure
                    }
                }
                worker.join()
                assertIs<IllegalStateException>(workerFailure)
                true
            }
            assertIs<ExecuteResult.Success<Boolean>>(read)
            assertFailsWith<IllegalStateException> { escapedRows!!.next() }
            ExecuteResult.Success("cross-thread use must roll back")
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(
            listOf("autoCommit:false", "prepare:SELECT 7 AS id", "executeQuery", "resultSet.close:id", "statement.close:SELECT 7 AS id", "rollback", "close"),
            events,
        )
    }

    @Test
    fun `opaque execution context does not expose JDBC types`() {
        val exposedTypes = (OperationExecutionContext::class.java.methods + OperationQueryRows::class.java.methods)
            .flatMap { method -> listOf(method.returnType) + method.parameterTypes }
        assertFalse(exposedTypes.any { Connection::class.java.isAssignableFrom(it) })
        assertFalse(exposedTypes.any { it.name.startsWith("java.sql.") })
        assertTrue(OperationExecutionContext::class.java.methods.none { it.name == "getConnection" })
        assertFalse(OperationQueryRows::class.java.methods.any { it.name.contains("ResultSet") })
    }

    private inner class ReadScript(
        private val events: MutableList<String>,
        private val boundValues: MutableList<List<Any?>> = mutableListOf(),
        private val rows: List<Pair<String, String>>,
        private val readFailure: SQLException? = null,
    ) {
        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args!![0] as String
                    events += "prepare:$sql"
                    statement(sql)
                }
                "setAutoCommit" -> { events += "autoCommit:${args!![0]}"; null }
                "commit", "rollback", "close" -> { events += method.name; null }
                "getAutoCommit" -> true
                "isClosed" -> false
                "toString" -> "ReadScriptConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun statement(sql: String): PreparedStatement {
            val selectedRow = if (sql.startsWith("SELECT", ignoreCase = true)) {
                rows.getOrNull(nextReadIndex++)
            } else {
                null
            }
            val resultSet = selectedRow?.let { (label, value) -> resultSet(label, value) }

            return Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "setObject" -> { boundValues += args!!.toList(); null }
                    "executeQuery" -> {
                        events += "executeQuery"
                        readFailure?.let { throw it }
                        requireNotNull(resultSet) { "No scripted row for $sql" }
                    }
                    "execute" -> { events += "execute"; true }
                    "close" -> { events += "statement.close:$sql"; null }
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as PreparedStatement
        }

        private var nextReadIndex = 0

        private fun resultSet(columnLabel: String, value: String): ResultSet {
            var onRow = false
            var consumed = false
            val metadata = Proxy.newProxyInstance(
                ResultSetMetaData::class.java.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getColumnCount" -> 1
                    "getColumnLabel", "getColumnName" -> if (args!![0] == 1) columnLabel else ""
                    else -> defaultValue(method.returnType)
                }
            } as ResultSetMetaData

            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getMetaData" -> metadata
                    "next" -> {
                        if (consumed) false else {
                            consumed = true
                            onRow = true
                            true
                        }
                    }
                    "getString" -> if (onRow && args!![0] == columnLabel) value else null
                    "getInt" -> if (onRow) value.toIntOrNull() ?: 0 else 0
                    "getLong" -> if (onRow) value.toLongOrNull() ?: 0L else 0L
                    "getBoolean" -> if (onRow) value.toBoolean() else false
                    "getDouble" -> if (onRow) value.toDoubleOrNull() ?: 0.0 else 0.0
                    "getBigDecimal" -> if (onRow) value.toBigDecimalOrNull() else null
                    "getBytes" -> if (onRow) value.toByteArray() else null
                    "wasNull" -> false
                    "close" -> { events += "resultSet.close:$columnLabel"; null }
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }
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
