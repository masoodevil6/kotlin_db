package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationDdlFailurePolicyTest {
    @Test
    fun `successful policy DDL commits`() {
        val events = mutableListOf<String>()
        val connection = connection(events)

        val result = executor(connection).transaction { scope ->
            val outcome = scope.executeDdlWithPolicy("CREATE TABLE sample (id INT)", emptyList()) {
                error("the policy must not run for successful DDL")
            }
            assertEquals(DdlExecutionOutcome.Success(true), outcome)
            ExecuteResult.Success("done")
        }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(listOf("autoCommit:false", "prepare:CREATE TABLE sample (id INT)", "execute:CREATE TABLE sample (id INT)", "statement.close", "commit", "close"), events)
    }

    @Test
    fun `legacy DDL failure remains fatal`() {
        val events = mutableListOf<String>()
        val sql = "ALTER TABLE sample ADD COLUMN value INT"
        val failure = SQLException("legacy DDL failed")
        val connection = connection(events, ddlFailures = mapOf(sql to failure))

        val result = executor(connection).transaction { scope ->
            assertSame(failure, assertIs<ExecuteResult.Failure>(scope.executeDdl(sql)).exception)
            ExecuteResult.Success("ignored")
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals("rollback", events[events.lastIndex - 1])
        assertEquals("close", events.last())
    }

    @Test
    fun `recoverable failure does not latch and callback can continue to commit`() {
        val events = mutableListOf<String>()
        val failingSql = "ALTER TABLE sample DROP COLUMN optional_value"
        val recovery = SQLException("column already absent")
        val connection = connection(events, ddlFailures = mapOf(failingSql to recovery))
        var classifications = 0

        val result = executor(connection).transaction { scope ->
            val outcome = scope.executeDdlWithPolicy(failingSql, emptyList()) {
                classifications++
                DdlFailureDisposition.RECOVERABLE
            }
            assertEquals(DdlExecutionOutcome.Failure(recovery, DdlFailureDisposition.RECOVERABLE), outcome)
            assertNull(scope.firstFailure)

            assertEquals(
                DdlExecutionOutcome.Success(true),
                scope.executeDdlWithPolicy("ALTER TABLE sample ADD COLUMN active INT", emptyList()) {
                    error("the policy must not run for successful DDL")
                },
            )
            ExecuteResult.Success("continued")
        }

        assertEquals(1, classifications)
        assertEquals("continued", assertIs<ExecuteResult.Success<String>>(result).result)
        assertTrue(events.contains("commit"))
        assertTrue("rollback" !in events)
    }

    @Test
    fun `fatal policy failure latches and prevents later DDL`() {
        val events = mutableListOf<String>()
        val sql = "ALTER TABLE sample ADD COLUMN duplicate_value INT"
        val failure = SQLException("fatal DDL")
        val connection = connection(events, ddlFailures = mapOf(sql to failure))
        var classifierCalls = 0

        val result = executor(connection).transaction { scope ->
            val outcome = scope.executeDdlWithPolicy(sql, emptyList()) {
                classifierCalls++
                DdlFailureDisposition.FATAL
            }
            assertEquals(DdlExecutionOutcome.Failure(failure, DdlFailureDisposition.FATAL), outcome)
            val later = scope.executeDdlWithPolicy("CREATE TABLE must_not_run (id INT)", emptyList()) {
                classifierCalls++
                DdlFailureDisposition.RECOVERABLE
            }
            assertSame(failure, assertIs<DdlExecutionOutcome.Failure>(later).exception)
            ExecuteResult.Success("must rollback")
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, classifierCalls)
        assertTrue(events.none { it.contains("must_not_run") })
        assertTrue("rollback" in events)
    }

    @Test
    fun `previous failure prevents DDL and classifier invocation`() {
        val events = mutableListOf<String>()
        val firstSql = "ALTER TABLE sample DROP COLUMN required_value"
        val firstFailure = SQLException("first failure")
        val connection = connection(events, ddlFailures = mapOf(firstSql to firstFailure))
        var secondClassifierCalls = 0

        val result = executor(connection).transaction { scope ->
            assertSame(firstFailure, assertIs<ExecuteResult.Failure>(scope.executeDdl(firstSql)).exception)
            val second = scope.executeDdlWithPolicy("CREATE TABLE must_not_run (id INT)", emptyList()) {
                secondClassifierCalls++
                DdlFailureDisposition.RECOVERABLE
            }
            assertSame(firstFailure, assertIs<DdlExecutionOutcome.Failure>(second).exception)
            ExecuteResult.Success("must rollback")
        }

        assertSame(firstFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, secondClassifierCalls)
        assertEquals(1, events.count { it.startsWith("prepare:") })
        assertTrue(events.none { it.contains("must_not_run") })
    }

    @Test
    fun `classifier exception is suppressed on the original failure and rolls back`() {
        val events = mutableListOf<String>()
        val sql = "ALTER TABLE sample ADD COLUMN rejected_value INT"
        val ddlFailure = SQLException("DDL failed")
        val classifierFailure = IllegalStateException("classifier failed")
        val connection = connection(events, ddlFailures = mapOf(sql to ddlFailure))
        var calls = 0

        val result = executor(connection).transaction { scope ->
            val outcome = scope.executeDdlWithPolicy(sql, emptyList()) {
                calls++
                throw classifierFailure
            }
            assertSame(ddlFailure, assertIs<DdlExecutionOutcome.Failure>(outcome).exception)
            assertEquals(DdlFailureDisposition.FATAL, outcome.disposition)
            ExecuteResult.Success("must rollback")
        }

        assertEquals(1, calls)
        assertSame(ddlFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(classifierFailure, ddlFailure.suppressed.single())
        assertTrue("rollback" in events)
    }

    @Test
    fun `classifier error is rethrown after rollback with original failure preserved`() {
        val events = mutableListOf<String>()
        val sql = "ALTER TABLE sample DROP COLUMN error_value"
        val ddlFailure = SQLException("DDL failed before classifier error")
        val classifierError = AssertionError("classifier error")
        val connection = connection(events, ddlFailures = mapOf(sql to ddlFailure))
        var capturedScope: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            executor(connection).transaction<String> { scope ->
                capturedScope = scope
                scope.executeDdlWithPolicy(sql, emptyList()) { throw classifierError }
                ExecuteResult.Success("unreachable")
            }
        }

        assertSame(classifierError, thrown)
        assertSame(ddlFailure, capturedScope?.firstFailure)
        assertSame(classifierError, capturedScope?.fatalError)
        assertSame(ddlFailure, classifierError.suppressed.single())
        assertEquals("rollback", events[events.lastIndex - 1])
        assertEquals("close", events.last())
    }

    @Test
    fun `DDL execution error is never classified and is rethrown after rollback`() {
        val events = mutableListOf<String>()
        val sql = "ALTER TABLE sample ADD COLUMN fatal_error INT"
        val ddlError = AssertionError("DDL execution error")
        val connection = connection(events, ddlFailures = mapOf(sql to ddlError))
        var classifierCalls = 0
        var capturedScope: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            executor(connection).transaction<String> { scope ->
                capturedScope = scope
                scope.executeDdlWithPolicy(sql, emptyList()) {
                    classifierCalls++
                    DdlFailureDisposition.RECOVERABLE
                }
                ExecuteResult.Success("unreachable")
            }
        }

        assertSame(ddlError, thrown)
        assertSame(ddlError, capturedScope?.firstFailure)
        assertSame(ddlError, capturedScope?.fatalError)
        assertEquals(0, classifierCalls)
        assertEquals("rollback", events[events.lastIndex - 1])
        assertEquals("close", events.last())
    }

    @Test
    fun `unsupported custom context fails closed through its failure mechanism`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        var capturedScope: OperationExecutionScope? = null

        val result = executor(connection).transaction { scope ->
            capturedScope = scope
            val unsupported = object : OperationExecutionContext {
                override fun executeDdl(
                    sql: String,
                    parameters: List<OperationParameter>,
                ): ExecuteResult<Boolean> = error("unsupported context must not fall back to executeDdl")

                override fun <T> failed(failure: Throwable): ExecuteResult<T> = scope.failed(failure)
            }

            val outcome = unsupported.executeDdlWithPolicy(
                "CREATE TABLE must_not_run (id INT)",
                emptyList(),
                DdlFailurePolicy { error("no DDL should be executed") },
            )
            assertEquals(DdlFailureDisposition.FATAL, assertIs<DdlExecutionOutcome.Failure>(outcome).disposition)
            ExecuteResult.Success("must rollback")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertIs<UnsupportedOperationException>(failure)
        assertSame(failure, capturedScope?.firstFailure)
        assertTrue(events.none { it.startsWith("prepare:") })
        assertTrue("rollback" in events)
    }

    @Test
    fun `recoverable success fatal sequence stops after fatal DDL`() {
        val events = mutableListOf<String>()
        val recoverableSql = "ALTER TABLE sample DROP COLUMN optional_value"
        val fatalSql = "ALTER TABLE sample ADD COLUMN invalid_value INT"
        val recoverableFailure = SQLException("recoverable")
        val fatalFailure = SQLException("fatal")
        val connection = connection(
            events,
            ddlFailures = mapOf(recoverableSql to recoverableFailure, fatalSql to fatalFailure),
        )
        var classifierCalls = 0

        val result = executor(connection).transaction { scope ->
            val recoverable = scope.executeDdlWithPolicy(recoverableSql, emptyList()) {
                classifierCalls++
                DdlFailureDisposition.RECOVERABLE
            }
            assertEquals(DdlFailureDisposition.RECOVERABLE, assertIs<DdlExecutionOutcome.Failure>(recoverable).disposition)

            assertEquals(
                DdlExecutionOutcome.Success(true),
                scope.executeDdlWithPolicy("ALTER TABLE sample ADD COLUMN good_value INT", emptyList()) {
                    error("the policy must not run for success")
                },
            )

            val fatal = scope.executeDdlWithPolicy(fatalSql, emptyList()) {
                classifierCalls++
                DdlFailureDisposition.FATAL
            }
            assertSame(fatalFailure, assertIs<DdlExecutionOutcome.Failure>(fatal).exception)

            val afterFatal = scope.executeDdlWithPolicy("CREATE TABLE after_fatal (id INT)", emptyList()) {
                classifierCalls++
                DdlFailureDisposition.RECOVERABLE
            }
            assertSame(fatalFailure, assertIs<DdlExecutionOutcome.Failure>(afterFatal).exception)
            ExecuteResult.Success("must rollback")
        }

        assertSame(fatalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(2, classifierCalls)
        assertTrue(events.any { it.contains("good_value") })
        assertTrue(events.none { it.contains("after_fatal") })
        assertTrue("rollback" in events)
    }

    private fun executor(connection: Connection) = OperationTransactionExecutor(
        connectionFactory = { connection },
        databasePlatformConfig = DatabasePlatformConfig(dialect = DialectQuery.MARIA_DB),
    )

    private fun connection(
        events: MutableList<String>,
        ddlFailures: Map<String, Throwable> = emptyMap(),
    ): Connection {
        var autoCommit = true
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "setAutoCommit" -> {
                    autoCommit = args!![0] as Boolean
                    events += "autoCommit:$autoCommit"
                    null
                }
                "getAutoCommit" -> autoCommit
                "prepareStatement" -> {
                    val sql = args!![0] as String
                    events += "prepare:$sql"
                    statement(sql, events, ddlFailures)
                }
                "commit", "rollback", "close" -> {
                    events += method.name
                    null
                }
                "isClosed" -> events.contains("close")
                "toString" -> "PolicyTestConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        }
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            handler,
        ) as Connection
    }

    private fun statement(
        sql: String,
        events: MutableList<String>,
        ddlFailures: Map<String, Throwable>,
    ): PreparedStatement = Proxy.newProxyInstance(
        PreparedStatement::class.java.classLoader,
        arrayOf(PreparedStatement::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "execute" -> {
                events += "execute:$sql"
                ddlFailures[sql]?.let { throw it }
                true
            }
            "close" -> {
                events += "statement.close"
                null
            }
            "isClosed" -> false
            else -> defaultValue(method.returnType)
        }
    } as PreparedStatement

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
