package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base_manager.execute.query.BoundQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Statement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TypedOperationExecutionTest {
    @Test
    fun `select supports BuiltQuery and preserves repeated parameter occurrences`() {
        val script = JdbcScript(
            rows = listOf(
                linkedMapOf<String, Any?>(
                    "first_value" to 7,
                    "second_value" to 7,
                ),
            ),
        )
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeSelect(
            BuiltQuery(
                "SELECT :value AS first_value, :value AS second_value",
                mutableListOf(QueryParameterBinding("value", 7, Types.INTEGER)),
            ),
        ) { rows ->
            assertEquals(listOf("first_value", "second_value"), rows.columnLabels)
            assertTrue(rows.next())
            assertEquals(7, rows.getInt("first_value"))
            assertEquals(7, rows.getInt("second_value"))
            assertFalse(rows.next())
            "read"
        }

        assertEquals(ExecuteResult.Success("read"), result)
        assertEquals(listOf(7, 7), script.boundValues.map { it.value })
        assertEquals(listOf(Types.INTEGER, Types.INTEGER), script.boundValues.map { it.sqlType })
        assertTrue(script.events.contains("resultSet.close"))
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `select supports BoundQuery and nullable parameters`() {
        val script = JdbcScript(
            rows = listOf(
                linkedMapOf<String, Any?>(
                    "first_value" to null,
                    "second_value" to null,
                ),
            ),
        )
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeSelect(
            BoundQuery(
                "SELECT ? AS first_value, ? AS second_value",
                listOf(
                    QueryParameterBinding("value", null, Types.INTEGER),
                    QueryParameterBinding("value", null, Types.INTEGER),
                ),
            ),
        ) { rows ->
            assertTrue(rows.next())
            assertEquals(null, rows.getInt("first_value"))
            assertEquals(null, rows.getInt("second_value"))
            null
        }

        assertIs<ExecuteResult.Success<String?>>(result)
        assertEquals(listOf(1, 2), script.boundValues.map { it.index })
        assertEquals(2, script.nullBindingCount)
        assertEquals(listOf(Types.INTEGER, Types.INTEGER), script.boundValues.map { it.sqlType })
        assertTrue(script.events.contains("resultSet.close"))
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `insert update and delete support both query representations`() {
        val builtInsertScript = JdbcScript(generatedKey = 41L)
        val builtInsert = OperationExecutionScope(builtInsertScript.connection).executeInsert(
            BuiltQuery(
                "INSERT INTO sample (name) VALUES (:name)",
                mutableListOf(QueryParameterBinding("name", "built", Types.VARCHAR)),
            ),
        )
        assertEquals(ExecuteResult.Success(41L), builtInsert)
        assertEquals("built", builtInsertScript.boundValues.single().value)
        assertNoScopeLifecycleOwnership(builtInsertScript)

        val boundInsertScript = JdbcScript(generatedKey = 42L)
        val boundInsert = OperationExecutionScope(boundInsertScript.connection).executeInsert(
            BoundQuery(
                "INSERT INTO sample (name) VALUES (?)",
                listOf(QueryParameterBinding("name", "bound", Types.VARCHAR)),
            ),
        )
        assertEquals(ExecuteResult.Success(42L), boundInsert)
        assertEquals("bound", boundInsertScript.boundValues.single().value)
        assertNoScopeLifecycleOwnership(boundInsertScript)

        val builtUpdateScript = JdbcScript(updateCount = 3)
        val builtUpdate = OperationExecutionScope(builtUpdateScript.connection).executeUpdate(
            BuiltQuery(
                "UPDATE sample SET name = :name",
                mutableListOf(QueryParameterBinding("name", "updated", Types.VARCHAR)),
            ),
        )
        assertEquals(ExecuteResult.Success(3), builtUpdate)
        assertNoScopeLifecycleOwnership(builtUpdateScript)

        val boundUpdateScript = JdbcScript(updateCount = 4)
        val boundUpdate = OperationExecutionScope(boundUpdateScript.connection).executeUpdate(
            BoundQuery(
                "UPDATE sample SET name = ?",
                listOf(QueryParameterBinding("name", "updated", Types.VARCHAR)),
            ),
        )
        assertEquals(ExecuteResult.Success(4), boundUpdate)
        assertNoScopeLifecycleOwnership(boundUpdateScript)

        val builtDeleteScript = JdbcScript(updateCount = 5)
        val builtDelete = OperationExecutionScope(builtDeleteScript.connection).executeDelete(
            BuiltQuery(
                "DELETE FROM sample WHERE id = :id",
                mutableListOf(QueryParameterBinding("id", 5, Types.INTEGER)),
            ),
        )
        assertEquals(ExecuteResult.Success(5), builtDelete)
        assertNoScopeLifecycleOwnership(builtDeleteScript)

        val boundDeleteScript = JdbcScript(updateCount = 6)
        val boundDelete = OperationExecutionScope(boundDeleteScript.connection).executeDelete(
            BoundQuery(
                "DELETE FROM sample WHERE id = ?",
                listOf(QueryParameterBinding("id", 6, Types.INTEGER)),
            ),
        )
        assertEquals(ExecuteResult.Success(6), boundDelete)
        assertNoScopeLifecycleOwnership(boundDeleteScript)
    }

    @Test
    fun `select callback failure is latched and resources are closed`() {
        val failure = IllegalStateException("mapping failed")
        val script = JdbcScript(rows = listOf(linkedMapOf<String, Any?>("value" to 1)))
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeSelect(
            BuiltQuery("SELECT 1 AS value", mutableListOf()),
        ) {
            throw failure
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(failure, scope.firstFailure)
        assertTrue(script.events.contains("resultSet.close"))
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `scope bound executor does not create a DatabaseContext or provider`() {
        val script = JdbcScript(
            rows = listOf(linkedMapOf<String, Any?>("value" to 12)),
            generatedKey = 901L,
        )
        val scope = OperationExecutionScope(script.connection)
        val executorBeforeExecution = scopeQueryExecutor(scope)

        assertEquals(
            ExecuteResult.Success(true),
            scope.executeDdl(
                "CREATE TABLE sample (id INT DEFAULT :id)",
                listOf(OperationParameter("id", 1, Types.INTEGER)),
            ),
        )
        assertEquals(
            DdlExecutionOutcome.Success(true),
            scope.executeDdlWithPolicy("ALTER TABLE sample ADD COLUMN value INT", emptyList()) {
                error("the policy must not run for successful DDL")
            },
        )
        assertEquals(
            ExecuteResult.Success(12),
            scope.executeRead("SELECT :value AS value", listOf(OperationParameter("value", 12, Types.INTEGER))) {
                rows ->
                assertTrue(rows.next())
                rows.getInt("value")
            },
        )
        assertEquals(
            ExecuteResult.Success(901L),
            scope.executeInsert(
                BoundQuery("INSERT INTO sample (value) VALUES (?)", listOf(QueryParameterBinding("value", 12, Types.INTEGER))),
            ),
        )
        assertEquals(
            ExecuteResult.Success(1),
            scope.executeUpdate(BuiltQuery("UPDATE sample SET value = 12", mutableListOf())),
        )
        scope.executeDelete(
            BoundQuery(
                "DELETE FROM sample WHERE id = ?",
                listOf(QueryParameterBinding("id", 1, Types.INTEGER)),
            ),
        )

        val executorAfterExecution = scopeQueryExecutor(scope)
        val contextField = executorAfterExecution.javaClass.getDeclaredField("databaseContext")
            .apply { isAccessible = true }
        val scopedConnectionField = executorAfterExecution.javaClass.getDeclaredField("scopedConnection")
            .apply { isAccessible = true }

        assertSame(executorBeforeExecution, executorAfterExecution)
        assertNull(contextField.get(executorAfterExecution))
        assertSame(script.connection, scopedConnectionField.get(executorAfterExecution))
        assertOnlyConnectionBoundQueryExecuteIsConstructed()
        assertEquals(6, script.events.count { it.startsWith("prepare:") })
        assertEquals(6, script.events.count { it == "statement.close" })
        assertEquals(2, script.events.count { it == "resultSet.close" })
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `DDL SQL failure remains primary when statement close also fails`() {
        val sqlFailure = SQLException("DDL failed")
        val closeFailure = SQLException("statement close failed")
        val script = JdbcScript(executionFailure = sqlFailure, statementCloseFailure = closeFailure)
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeDdl("CREATE TABLE sample (id INT)")

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(sqlFailure, failure)
        assertTrue(sqlFailure.suppressed.any { it === closeFailure })
        assertSame(sqlFailure, scope.firstFailure)
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `executeRead SQL failure remains primary when statement close also fails`() {
        val sqlFailure = SQLException("read failed")
        val closeFailure = SQLException("statement close failed")
        val script = JdbcScript(executionFailure = sqlFailure, statementCloseFailure = closeFailure)
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeRead("SELECT value FROM sample") {
            error("the row callback must not run after SQL failure")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(sqlFailure, failure)
        assertTrue(sqlFailure.suppressed.any { it === closeFailure })
        assertSame(sqlFailure, scope.firstFailure)
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `executeRead callback failure remains primary when statement close also fails`() {
        val callbackFailure = IllegalStateException("row callback failed")
        val closeFailure = SQLException("statement close failed")
        val script = JdbcScript(
            rows = listOf(linkedMapOf<String, Any?>("value" to 1)),
            statementCloseFailure = closeFailure,
        )
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeRead("SELECT value FROM sample") { throw callbackFailure }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(callbackFailure, failure)
        assertTrue(callbackFailure.suppressed.any { it === closeFailure })
        assertSame(callbackFailure, scope.firstFailure)
        assertTrue(script.events.contains("resultSet.close"))
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `select SQL failure remains primary when statement close also fails`() {
        val sqlFailure = SQLException("select failed")
        val closeFailure = SQLException("statement close failed")
        val script = JdbcScript(
            executionFailure = sqlFailure,
            statementCloseFailure = closeFailure,
        )
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeSelect(
            BuiltQuery("SELECT value FROM sample", mutableListOf()),
        ) { error("the row callback must not run after SQL failure") }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(sqlFailure, failure)
        assertTrue(sqlFailure.suppressed.any { it === closeFailure })
        assertSame(sqlFailure, scope.firstFailure)
        assertTrue(script.events.contains("statement.close"))
    }

    @Test
    fun `select callback failure remains primary when statement close also fails`() {
        val callbackFailure = IllegalStateException("mapping failed")
        val closeFailure = SQLException("statement close failed")
        val script = JdbcScript(
            rows = listOf(linkedMapOf<String, Any?>("value" to 1)),
            statementCloseFailure = closeFailure,
        )
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeSelect(
            BuiltQuery("SELECT value FROM sample", mutableListOf()),
        ) { throw callbackFailure }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(callbackFailure, failure)
        assertTrue(callbackFailure.suppressed.any { it === closeFailure })
        assertSame(callbackFailure, scope.firstFailure)
        assertTrue(script.events.contains("resultSet.close"))
        assertTrue(script.events.contains("statement.close"))
    }

    @Test
    fun `typed select rethrows Error records it as fatal and closes resources`() {
        val fatal = AssertionError("fatal mapping error")
        val script = JdbcScript(rows = listOf(linkedMapOf<String, Any?>("value" to 1)))
        val scope = OperationExecutionScope(script.connection)

        val thrown = assertFailsWith<AssertionError> {
            scope.executeSelect(
                BuiltQuery("SELECT value FROM sample", mutableListOf()),
            ) { throw fatal }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertTrue(script.events.contains("resultSet.close"))
        assertTrue(script.events.contains("statement.close"))
    }

    @Test
    fun `query rows reject cross thread access`() {
        val script = JdbcScript(rows = listOf(linkedMapOf<String, Any?>("value" to 1)))
        val scope = OperationExecutionScope(script.connection)
        var crossThreadFailure: Throwable? = null

        val result = scope.executeSelect(
            BuiltQuery("SELECT value FROM sample", mutableListOf()),
        ) { rows ->
            val thread = Thread {
                try {
                    rows.next()
                } catch (failure: Throwable) {
                    crossThreadFailure = failure
                }
            }
            thread.start()
            thread.join()
            "read"
        }

        assertEquals(ExecuteResult.Success("read"), result)
        assertIs<IllegalStateException>(crossThreadFailure)
        assertSame(crossThreadFailure, scope.firstFailure)
    }

    @Test
    fun `select callback is synchronous and runs before the method returns`() {
        val script = JdbcScript(rows = listOf(linkedMapOf<String, Any?>("value" to 1)))
        val scope = OperationExecutionScope(script.connection)
        val events = mutableListOf<String>()

        val result = scope.executeSelect(
            BuiltQuery("SELECT value FROM sample", mutableListOf()),
        ) {
            events += "callback"
            "read"
        }
        events += "returned"

        assertEquals(ExecuteResult.Success("read"), result)
        assertEquals(listOf("callback", "returned"), events)
        assertTrue(script.events.indexOf("executeQuery") < script.events.indexOf("statement.close"))
    }

    @Test
    fun `insert closes generated keys result set`() {
        val script = JdbcScript(generatedKey = 99L)
        val scope = OperationExecutionScope(script.connection)

        assertEquals(
            ExecuteResult.Success(99L),
            scope.executeInsert(
                BoundQuery(
                    "INSERT INTO sample (value) VALUES (?)",
                    listOf(QueryParameterBinding("value", 1, Types.INTEGER)),
                ),
            ),
        )

        assertTrue(script.events.count { it == "resultSet.close" } >= 1)
    }

    @Test
    fun `default typed context methods fail closed`() {
        val context = object : OperationExecutionContext {
            override fun executeDdl(
                sql: String,
                parameters: List<OperationParameter>,
            ): ExecuteResult<Boolean> = ExecuteResult.Success(true)

            override fun <T> failed(failure: Throwable): ExecuteResult<T> = ExecuteResult.Failure(failure)
        }

        val failure = assertIs<ExecuteResult.Failure>(
            context.executeInsert(BuiltQuery("INSERT INTO sample DEFAULT VALUES", mutableListOf())),
        ).exception
        assertIs<UnsupportedOperationException>(failure)
    }

    @Test
    fun `nullable BuiltQuery binding preserves SQL type`() {
        val script = JdbcScript(rows = listOf(linkedMapOf<String, Any?>("value" to null)))
        val scope = OperationExecutionScope(script.connection)

        scope.executeSelect(
            BuiltQuery(
                "SELECT :value AS value",
                mutableListOf(QueryParameterBinding("value", null, Types.INTEGER)),
            ),
        ) { rows ->
            assertTrue(rows.next())
            assertNull(rows.getInt("value"))
            "read"
        }

        assertEquals(1, script.nullBindingCount)
        assertEquals(Types.INTEGER, script.boundValues.single().sqlType)
    }

    @Test
    fun `SQL execution failure is propagated and latched for DML`() {
        val failure = SQLException("update failed")
        val script = JdbcScript(executionFailure = failure)
        val scope = OperationExecutionScope(script.connection)

        val result = scope.executeUpdate(
            BoundQuery(
                "UPDATE sample SET value = ?",
                listOf(QueryParameterBinding("value", 1, Types.INTEGER)),
            ),
        )

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(failure, scope.firstFailure)
        assertTrue(script.events.contains("statement.close"))
        assertNoScopeLifecycleOwnership(script)
    }

    @Test
    fun `typed operations reject an ended scope before touching JDBC`() {
        val script = JdbcScript()
        val scope = OperationExecutionScope(script.connection)
        scope.endForLifecycle()

        val result = scope.executeDelete(
            BoundQuery(
                "DELETE FROM sample WHERE id = ?",
                listOf(QueryParameterBinding("id", 1, Types.INTEGER)),
            ),
        )

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("scope has ended", ignoreCase = true))
        assertTrue(script.events.none { it.startsWith("prepare:") })
        assertNoScopeLifecycleOwnership(script)
    }

    private fun assertNoScopeLifecycleOwnership(script: JdbcScript) {
        assertFalse(script.events.contains("commit"))
        assertFalse(script.events.contains("rollback"))
        assertFalse(script.events.contains("connection.close"))
    }

    private fun scopeQueryExecutor(scope: OperationExecutionScope): Any {
        val executorField = OperationExecutionScope::class.java.getDeclaredField("typedQueryExecutor")
            .apply { isAccessible = true }
        return requireNotNull(executorField.get(scope))
    }

    /**
     * The legacy no-arg constructor only allocates a DatabaseContext/provider, so JDBC proxies
     * cannot observe it. Guard the Scope source to ensure the only QueryExecute construction is
     * the one explicitly bound to its Connection.
     */
    private fun assertOnlyConnectionBoundQueryExecuteIsConstructed() {
        val classesDirectory = Path.of(
            OperationExecutionScope::class.java.protectionDomain.codeSource.location.toURI(),
        )
        val moduleDirectory = classesDirectory.parent.parent.parent.parent
        val sourceFile = moduleDirectory.resolve("src/main/kotlin/transaction/OperationExecutionScope.kt")
        assertTrue(Files.isRegularFile(sourceFile), "OperationExecutionScope source should be available to this test")

        val constructors = Regex("\\bQueryExecute\\s*\\(([^)]*)\\)")
            .findAll(Files.readString(sourceFile))
            .map { it.groupValues[1].trim() }
            .toList()
        assertEquals(listOf("jdbcConnection"), constructors)
    }

    private class JdbcScript(
        private val rows: List<LinkedHashMap<String, Any?>> = emptyList(),
        private val generatedKey: Long = 1L,
        private val updateCount: Int = 1,
        private val executionFailure: SQLException? = null,
        private val statementCloseFailure: SQLException? = null,
    ) {
        val events = mutableListOf<String>()
        val boundValues = mutableListOf<Binding>()
        var nullBindingCount: Int = 0
            private set

        private var readIndex = 0

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args?.firstOrNull() as? String ?: error("SQL is required")
                    events += "prepare:$sql"
                    statement(sql)
                }
                "commit", "rollback" -> {
                    events += method.name
                    null
                }
                "close" -> {
                    events += "connection.close"
                    null
                }
                "getAutoCommit" -> true
                "isClosed" -> false
                "toString" -> "TypedOperationConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun statement(sql: String): PreparedStatement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            when (method.name) {
                "setObject" -> {
                    val actualArgs = requireNotNull(args)
                    boundValues += Binding(
                        index = actualArgs[0] as Int,
                        value = actualArgs[1],
                        sqlType = actualArgs[2] as Int,
                    )
                    null
                }
                "setNull" -> {
                    val actualArgs = requireNotNull(args)
                    nullBindingCount++
                    boundValues += Binding(
                        index = actualArgs[0] as Int,
                        value = null,
                        sqlType = actualArgs[1] as Int,
                    )
                    null
                }
                "executeQuery" -> {
                    events += "executeQuery"
                    executionFailure?.let { throw it }
                    resultSet(rows.getOrNull(readIndex++) ?: linkedMapOf())
                }
                "executeUpdate" -> {
                    events += "executeUpdate"
                    executionFailure?.let { throw it }
                    updateCount
                }
                "execute" -> {
                    events += "execute"
                    executionFailure?.let { throw it }
                    false
                }
                "getGeneratedKeys" -> generatedKeys()
                "close" -> {
                    events += "statement.close"
                    statementCloseFailure?.let { throw it }
                    null
                }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        private fun generatedKeys(): ResultSet = resultSet(
            listOf(linkedMapOf<String, Any?>("generated_key" to generatedKey)),
        )

        private fun resultSet(row: LinkedHashMap<String, Any?>): ResultSet = resultSet(listOf(row))

        private fun resultSet(rows: List<LinkedHashMap<String, Any?>>): ResultSet {
            var index = -1
            var lastWasNull = false
            val columns = rows.firstOrNull()?.keys?.toList().orEmpty()
            fun value(
                args: Array<out Any?>?,
                columns: List<String>,
                rows: List<LinkedHashMap<String, Any?>>,
                index: Int,
            ): Any? {
                if (index !in rows.indices) {
                    lastWasNull = true
                    return null
                }
                val key = when (val selector = args?.firstOrNull()) {
                    is Int -> columns[selector - 1]
                    is String -> selector
                    else -> error("A column selector is required")
                }
                val selected = rows[index][key]
                lastWasNull = selected == null
                return selected
            }
            val metadata = Proxy.newProxyInstance(
                ResultSetMetaData::class.java.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getColumnCount" -> columns.size
                    "getColumnLabel", "getColumnName" -> columns[(args?.get(0) as Int) - 1]
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
                        index++
                        index < rows.size
                    }
                    "getString" -> value(args, columns, rows, index)?.toString()
                    "getInt" -> value(args, columns, rows, index).toIntOrNullValue()
                    "getLong" -> value(args, columns, rows, index).toLongOrNullValue()
                    "getBoolean" -> value(args, columns, rows, index).toBooleanOrNullValue()
                    "getDouble" -> value(args, columns, rows, index).toDoubleOrNullValue()
                    "getBigDecimal" -> value(args, columns, rows, index) as? java.math.BigDecimal
                    "getBytes" -> value(args, columns, rows, index) as? ByteArray
                    "wasNull" -> lastWasNull
                    "close" -> {
                        events += "resultSet.close"
                        null
                    }
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }

        private fun Any?.toIntOrNullValue(): Int {
            if (this == null) return 0
            return when (this) {
                is Number -> this.toInt()
                else -> toString().toInt()
            }
        }

        private fun Any?.toLongOrNullValue(): Long {
            if (this == null) return 0L
            return when (this) {
                is Number -> this.toLong()
                else -> toString().toLong()
            }
        }

        private fun Any?.toBooleanOrNullValue(): Boolean {
            if (this == null) return false
            return this as? Boolean ?: toString().toBoolean()
        }

        private fun Any?.toDoubleOrNullValue(): Double {
            if (this == null) return 0.0
            return when (this) {
                is Number -> this.toDouble()
                else -> toString().toDouble()
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

        data class Binding(
            val index: Int,
            val value: Any?,
            val sqlType: Int,
        )
    }
}
