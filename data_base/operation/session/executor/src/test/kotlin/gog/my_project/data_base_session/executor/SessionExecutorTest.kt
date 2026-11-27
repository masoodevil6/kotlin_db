package gog.my_project.data_base_session.executor

import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.acquire.IAcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.interfaces.advisory_lock.release.IReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.metadata.advisory_lock.acquire.AcquireSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.metadata.advisory_lock.release.ReleaseSessionAdvisoryLockAstMetadata
import gog.my_project.data_base_session.ast.schema.advisory_lock.acquire.AcquireSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.schema.advisory_lock.release.ReleaseSessionAdvisoryLockAst
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.dialect.data_class.SessionCommandParameter
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_session.executor.exceptions.SessionExecutionException
import gog.my_project.data_base_session.executor.exceptions.SessionProtocolException
import gog.my_project.data_base_session.executor.manager.SessionExecutor
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.SQLTimeoutException
import java.sql.Statement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class SessionExecutorTest {
    private val executor = SessionExecutor()

    @Test
    fun `acquire renders and binds SQL parameters in placeholder order`() {
        listOf(DatabaseDialect.MYSQL, DatabaseDialect.MARIA_DB).forEach { dialect ->
            val jdbc = JdbcFake(rows = listOf(listOf<Any?>(1)))

            val result = executor.acquire(acquireAst(), jdbc.connection, dialect)

            assertEquals(AcquireSessionLockResult.Acquired, result)
            assertEquals("SELECT GET_LOCK(?, ?) AS acquired", jdbc.sql)
            assertEquals(
                listOf(
                    Binding(1, "kdb1_22f00cc29957a0c2cfb8e5b0106ce5b4dd546ef0173cc52970e19a5fa6", Types.VARCHAR),
                    Binding(2, 0, Types.INTEGER),
                ),
                jdbc.bindings,
            )
            assertOwnedResourcesClosed(jdbc)
        }
    }

    @Test
    fun `acquire binds the configured timeout as the second integer parameter`() {
        val jdbc = JdbcFake(rows = listOf(listOf<Any?>(1)))

        assertEquals(
            AcquireSessionLockResult.Acquired,
            executor.acquire(acquireAst(LockWaitPolicy.Timeout.of(30.seconds)), jdbc.connection, DatabaseDialect.MYSQL),
        )
        assertEquals(30, jdbc.bindings[1].value)
        assertEquals(Types.INTEGER, jdbc.bindings[1].sqlType)
    }

    @Test
    fun `binder uses setNull with the parameter SQL type`() {
        val jdbc = JdbcFake()
        val bind = SessionExecutor::class.java.getDeclaredMethod(
            "bind",
            PreparedStatement::class.java,
            List::class.java,
        ).apply { isAccessible = true }

        bind.invoke(
            executor,
            jdbc.statementForBindingTest,
            listOf(SessionCommandParameter("nullable", null, Types.VARCHAR)),
        )

        assertEquals(listOf(Binding(1, null, Types.VARCHAR)), jdbc.bindings)
    }

    @Test
    fun `acquire distinguishes acquired and timed out results`() {
        val acquired = JdbcFake(rows = listOf(listOf<Any?>(1)))
        val timedOut = JdbcFake(rows = listOf(listOf<Any?>(0)))

        assertEquals(
            AcquireSessionLockResult.Acquired,
            executor.acquire(acquireAst(), acquired.connection, DatabaseDialect.MYSQL),
        )
        assertEquals(
            AcquireSessionLockResult.TimedOut,
            executor.acquire(acquireAst(), timedOut.connection, DatabaseDialect.MYSQL),
        )
    }

    @Test
    fun `accepts only the agreed integral wrapper types for statuses`() {
        listOf<Any>(1.toByte(), 1.toShort(), 1, 1L).forEach { value ->
            val jdbc = JdbcFake(rows = listOf(listOf(value)))
            assertEquals(
                AcquireSessionLockResult.Acquired,
                executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL),
            )
        }

        listOf<Any>(2L, -1L).forEach { value ->
            val jdbc = JdbcFake(rows = listOf(listOf(value)))
            assertIs<SessionProtocolException>(runCatching {
                executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull())
        }
    }

    @Test
    fun `acquire rejects null unsupported values and out of range integers`() {
        val invalidValues = listOf(null, 2, -1, 1.0, 1.5, "1", true, java.math.BigInteger.ONE)

        invalidValues.forEach { value ->
            val jdbc = JdbcFake(rows = listOf(listOf(value)))
            assertIs<SessionProtocolException>(runCatching {
                executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull())
        }
    }

    @Test
    fun `release binds one lock name and maps released not owned and null`() {
        val expectations = listOf(
            1 to ReleaseSessionLockResult.Released,
            0 to ReleaseSessionLockResult.NotOwned,
            null to ReleaseSessionLockResult.NotOwned,
        )

        expectations.forEach { (value, expected) ->
            val jdbc = JdbcFake(rows = listOf(listOf(value)))

            assertEquals(expected, executor.release(releaseAst(), jdbc.connection, DatabaseDialect.MARIA_DB))
            assertEquals("SELECT RELEASE_LOCK(?) AS released", jdbc.sql)
            assertEquals(
                listOf(
                    Binding(1, "kdb1_22f00cc29957a0c2cfb8e5b0106ce5b4dd546ef0173cc52970e19a5fa6", Types.VARCHAR),
                ),
                jdbc.bindings,
            )
            assertOwnedResourcesClosed(jdbc)
        }
    }

    @Test
    fun `release rejects unsupported values and out of range integers`() {
        listOf(2, -1, 1.0, 1.5, "1", true, java.math.BigInteger.ONE).forEach { value ->
            val jdbc = JdbcFake(rows = listOf(listOf(value)))
            assertIs<SessionProtocolException>(runCatching {
                executor.release(releaseAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull())
        }
    }

    @Test
    fun `rejects empty multi row and invalid column result sets`() {
        val scenarios = listOf(
            JdbcFake(rows = emptyList()) to "empty rows",
            JdbcFake(rows = listOf(listOf<Any?>(1), listOf<Any?>(1))) to "multiple rows",
            JdbcFake(rows = listOf(listOf<Any?>(1)), columnCount = 0) to "zero columns",
            JdbcFake(rows = listOf(listOf<Any?>(1)), columnCount = 2) to "multiple columns",
        )

        scenarios.forEach { (jdbc, _) ->
            assertIs<SessionProtocolException>(runCatching {
                executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull())
            assertTrue(jdbc.resultSetClosed)
            assertTrue(jdbc.statementClosed)
        }
    }

    @Test
    fun `rejects absent initial result set update counts and additional results`() {
        val scenarios = listOf(
            JdbcFake(hasInitialResultSet = false) to "no result",
            JdbcFake(hasInitialResultSet = false, initialUpdateCount = 1) to "initial update count",
            JdbcFake(additionalResultSet = true) to "additional result set",
            JdbcFake(additionalUpdateCount = 1) to "additional update count",
        )

        scenarios.forEach { (jdbc, _) ->
            assertIs<SessionProtocolException>(runCatching {
                executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull())
            assertTrue(jdbc.statementClosed)
            assertFalse(jdbc.connectionClosed)
        }
    }

    @Test
    fun `rejects execute true when the initial result set is missing`() {
        val jdbc = JdbcFake(resultSetAvailable = false)

        assertIs<SessionProtocolException>(runCatching {
            executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
        }.exceptionOrNull())

        assertEquals(0, jdbc.resultSetCloseCalls)
        assertEquals(0, jdbc.resultSetImplicitCloseCalls)
        assertTrue(jdbc.statementClosed)
        assertFalse(jdbc.connectionClosed)
    }

    @Test
    fun `release rejects invalid result set shapes and closes owned resources`() {
        val scenarios = listOf(
            JdbcFake(rows = emptyList()),
            JdbcFake(rows = listOf(listOf<Any?>(1), listOf<Any?>(1))),
            JdbcFake(rows = listOf(listOf<Any?>(1)), columnCount = 0),
            JdbcFake(rows = listOf(listOf<Any?>(1)), columnCount = 2),
        )

        scenarios.forEach { jdbc ->
            assertIs<SessionProtocolException>(runCatching {
                executor.release(releaseAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull())
            assertTrue(jdbc.resultSetClosed)
            assertEquals(1, jdbc.resultSetCloseCalls)
            assertEquals(0, jdbc.resultSetImplicitCloseCalls)
            assertTrue(jdbc.statementClosed)
            assertFalse(jdbc.connectionClosed)
        }
    }

    @Test
    fun `SQL timeouts and JDBC failures remain execution exceptions with original causes`() {
        val timeout = SQLTimeoutException("driver timed out")
        val timeoutJdbc = JdbcFake(failures = mapOf("execute" to timeout))
        val timeoutFailure = runCatching {
            executor.acquire(acquireAst(), timeoutJdbc.connection, DatabaseDialect.MYSQL)
        }.exceptionOrNull()
        assertIs<SessionExecutionException>(timeoutFailure)
        assertSame(timeout, timeoutFailure.cause)

        val failingStages = listOf(
            "prepareStatement",
            "setObject",
            "execute",
            "getMetaData",
            "getColumnCount",
            "next",
            "getObject",
            "getMoreResults",
        )
        failingStages.forEach { stage ->
            val cause = SQLException("failed at $stage")
            val jdbc = JdbcFake(failures = mapOf(stage to cause))
            val failure = runCatching {
                executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
            }.exceptionOrNull()

            assertIs<SessionExecutionException>(failure, stage)
            assertSame(cause, failure.cause, stage)
        }
    }

    @Test
    fun `resource close failures are surfaced or suppressed without closing connection`() {
        val primary = SQLException("execute failed")
        val statementClose = SQLException("statement close failed")
        val jdbcWithPrimaryFailure = JdbcFake(
            failures = mapOf("execute" to primary),
            statementCloseFailure = statementClose,
        )
        val executionFailure = runCatching {
            executor.acquire(acquireAst(), jdbcWithPrimaryFailure.connection, DatabaseDialect.MYSQL)
        }.exceptionOrNull()
        assertIs<SessionExecutionException>(executionFailure)
        assertSame(primary, executionFailure.cause)
        assertTrue(primary.suppressed.any { it === statementClose })
        assertTrue(jdbcWithPrimaryFailure.statementClosed)
        assertFalse(jdbcWithPrimaryFailure.connectionClosed)

        val protocolResultSetClose = SQLException("result set close failed")
        val protocolStatementClose = SQLException("statement close failed")
        val jdbcWithProtocolFailure = JdbcFake(
            rows = emptyList(),
            resultSetCloseFailure = protocolResultSetClose,
            statementCloseFailure = protocolStatementClose,
        )
        val protocolFailure = runCatching {
            executor.acquire(acquireAst(), jdbcWithProtocolFailure.connection, DatabaseDialect.MYSQL)
        }.exceptionOrNull()
        assertIs<SessionProtocolException>(protocolFailure)
        assertTrue(protocolFailure.suppressed.any { it === protocolResultSetClose })
        assertTrue(protocolFailure.suppressed.any { it === protocolStatementClose })
        assertTrue(jdbcWithProtocolFailure.resultSetClosed)
        assertTrue(jdbcWithProtocolFailure.statementClosed)
        assertFalse(jdbcWithProtocolFailure.connectionClosed)
        assertEquals(0, jdbcWithProtocolFailure.commitCalls)
        assertEquals(0, jdbcWithProtocolFailure.rollbackCalls)
    }

    @Test
    fun `close on result set closed by getMoreResults is harmless`() {
        val statementClose = SQLException("statement close failed")
        val jdbc = JdbcFake(
            rows = listOf(listOf<Any?>(1)),
            statementCloseFailure = statementClose,
        )

        val failure = runCatching {
            executor.acquire(acquireAst(), jdbc.connection, DatabaseDialect.MYSQL)
        }.exceptionOrNull()

        assertIs<SessionExecutionException>(failure)
        assertSame(statementClose, failure.cause)
        assertTrue(jdbc.resultSetClosed)
        assertEquals(1, jdbc.resultSetImplicitCloseCalls)
        assertEquals(1, jdbc.resultSetCloseCalls)
        assertTrue(jdbc.statementClosed)
        assertFalse(jdbc.connectionClosed)
    }

    private fun acquireAst(waitPolicy: LockWaitPolicy = LockWaitPolicy.NoWait): IAcquireSessionAdvisoryLockAst =
        AcquireSessionAdvisoryLockAst(
            AcquireSessionAdvisoryLockAstMetadata().apply {
                resource = LockResource("migration", "database-main")
                this.waitPolicy = waitPolicy
            },
        )

    private fun releaseAst(): IReleaseSessionAdvisoryLockAst =
        ReleaseSessionAdvisoryLockAst(
            ReleaseSessionAdvisoryLockAstMetadata().apply {
                resource = LockResource("migration", "database-main")
            },
        )

    private fun assertOwnedResourcesClosed(jdbc: JdbcFake) {
        assertTrue(jdbc.resultSetClosed)
        assertEquals(1, jdbc.resultSetImplicitCloseCalls)
        assertEquals(1, jdbc.resultSetCloseCalls)
        assertTrue(jdbc.statementClosed)
        assertFalse(jdbc.connectionClosed)
        assertEquals(0, jdbc.commitCalls)
        assertEquals(0, jdbc.rollbackCalls)
    }

    private data class Binding(
        val index: Int,
        val value: Any?,
        val sqlType: Int,
    )

    private class JdbcFake(
        private val hasInitialResultSet: Boolean = true,
        private val resultSetAvailable: Boolean = hasInitialResultSet,
        private val initialUpdateCount: Int = -1,
        private val additionalResultSet: Boolean = false,
        private val additionalUpdateCount: Int = -1,
        private val rows: List<List<Any?>> = listOf(listOf(1)),
        private val columnCount: Int = 1,
        private val failures: Map<String, SQLException> = emptyMap(),
        private val resultSetCloseFailure: SQLException? = null,
        private val statementCloseFailure: SQLException? = null,
    ) {
        val bindings = mutableListOf<Binding>()
        var sql: String? = null
            private set
        var resultSetClosed = false
            private set
        var resultSetCloseCalls = 0
            private set
        var resultSetImplicitCloseCalls = 0
            private set
        var statementClosed = false
            private set
        var connectionClosed = false
            private set
        var commitCalls = 0
            private set
        var rollbackCalls = 0
            private set

        private var rowIndex = -1
        private var afterMoreResults = false

        private fun failAt(stage: String) {
            failures[stage]?.let { throw it }
        }

        private fun closeResultSetExplicitly() {
            resultSetCloseCalls++
            if (!resultSetClosed) {
                resultSetClosed = true
                resultSetCloseFailure?.let { throw it }
            }
        }

        private fun closeCurrentResultSet() {
            resultSetImplicitCloseCalls++
            if (!resultSetClosed) {
                resultSetClosed = true
                resultSetCloseFailure?.let { throw it }
            }
        }

        private val metadata: ResultSetMetaData = proxy(ResultSetMetaData::class.java) { _, method, _ ->
            when (method.name) {
                "getColumnCount" -> {
                    failAt("getColumnCount")
                    columnCount
                }
                else -> defaultValue(method.returnType)
            }
        }

        private val resultSet: ResultSet = proxy(ResultSet::class.java) { _, method, args ->
            when (method.name) {
                "getMetaData" -> {
                    failAt("getMetaData")
                    metadata
                }
                "next" -> {
                    failAt("next")
                    rowIndex++
                    rowIndex < rows.size
                }
                "getObject" -> {
                    failAt("getObject")
                    val columnIndex = args!![0] as Int
                    rows[rowIndex][columnIndex - 1]
                }
                "close" -> {
                    closeResultSetExplicitly()
                    null
                }
                else -> defaultValue(method.returnType)
            }
        }

        private val statement: PreparedStatement = proxy(PreparedStatement::class.java) { _, method, args ->
            when (method.name) {
                "setObject" -> {
                    failAt("setObject")
                    bindings += Binding(args!![0] as Int, args[1], args[2] as Int)
                    null
                }
                "setNull" -> {
                    failAt("setNull")
                    bindings += Binding(args!![0] as Int, null, args[1] as Int)
                    null
                }
                "execute" -> {
                    failAt("execute")
                    hasInitialResultSet
                }
                "getResultSet" -> if (resultSetAvailable) resultSet else null
                "getUpdateCount" -> if (afterMoreResults) additionalUpdateCount else initialUpdateCount
                "getMoreResults" -> {
                    failAt("getMoreResults")
                    assertEquals(Statement.CLOSE_CURRENT_RESULT, args!![0])
                    closeCurrentResultSet()
                    afterMoreResults = true
                    additionalResultSet
                }
                "close" -> {
                    statementClosed = true
                    statementCloseFailure?.let { throw it }
                    null
                }
                else -> defaultValue(method.returnType)
            }
        }

        val statementForBindingTest: PreparedStatement
            get() = statement

        val connection: Connection = proxy(Connection::class.java) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    failAt("prepareStatement")
                    sql = args!![0] as String
                    statement
                }
                "close" -> {
                    connectionClosed = true
                    null
                }
                "commit" -> {
                    commitCalls++
                    null
                }
                "rollback" -> {
                    rollbackCalls++
                    null
                }
                else -> defaultValue(method.returnType)
            }
        }
    }

    private companion object {
        fun <T> proxy(
            type: Class<T>,
            handler: (Any, Method, Array<out Any?>?) -> Any?,
        ): T = type.cast(
            Proxy.newProxyInstance(
                type.classLoader,
                arrayOf(type),
                java.lang.reflect.InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "toString" -> "JDBC fake ${type.simpleName}"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    else -> handler(proxy, method, args)
                }
                },
            ),
        )

        fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0.0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
