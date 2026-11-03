package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class OperationTransactionExecutorTest {
    @Test
    fun `success commits and closes its single owned connection`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executor = OperationTransactionExecutor { connection }

        val result = executor.transaction { scope ->
            assertSame(connection, scope.connection)
            ExecuteResult.Success(null)
        }

        assertIs<ExecuteResult.Success<*>>(result)
        assertNull(result.result)
        assertEquals(listOf("autoCommit:false", "commit", "close"), events)
    }

    @Test
    fun `returned failure is preserved and rolls back`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = SQLException("query failed")

        val result = OperationTransactionExecutor { connection }.transaction {
            ExecuteResult.Failure(failure)
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `latched operation failure prevents commit even when callback returns success`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = SQLException("ignored operation failure")

        val result = OperationTransactionExecutor { connection }.transaction { scope ->
            scope.recordResult(ExecuteResult.Failure(failure))
            ExecuteResult.Success("ignored")
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `callback exception becomes failure and rolls back`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = IllegalStateException("callback failed")

        val result = OperationTransactionExecutor { connection }.transaction<String> {
            throw failure
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `callback error is rethrown after rollback and close`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = AssertionError("fatal callback")

        val thrown = assertFailsWith<AssertionError> {
            OperationTransactionExecutor { connection }.transaction<String> {
                throw failure
            }
        }

        assertSame(failure, thrown)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `commit failure is wrapped then rollback and close are attempted`() {
        val events = mutableListOf<String>()
        val commitError = SQLException("commit failed")
        val connection = connection(events, failures = mapOf("commit" to commitError))

        val result = OperationTransactionExecutor { connection }.transaction {
            ExecuteResult.Success("value")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        val commitFailure = assertIs<OperationTransactionCommitException>(failure)
        assertSame(commitError, commitFailure.cause)
        assertEquals(listOf("autoCommit:false", "commit", "rollback", "close"), events)
    }

    @Test
    fun `rollback error remains primary and close is still attempted`() {
        val events = mutableListOf<String>()
        val original = IllegalStateException("operation failed")
        val rollbackError = AssertionError("rollback failed")
        val closeFailure = SQLException("close failed")
        val connection = connection(
            events,
            failures = mapOf("rollback" to rollbackError, "close" to closeFailure),
        )

        val thrown = assertFailsWith<AssertionError> {
            OperationTransactionExecutor { connection }.transaction<String> {
                ExecuteResult.Failure(original)
            }
        }

        assertSame(rollbackError, thrown)
        assertEquals(listOf(original, closeFailure), thrown.suppressed.toList())
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `close failure after commit reports committed outcome without rollback`() {
        val events = mutableListOf<String>()
        val closeError = SQLException("close failed")
        val connection = connection(events, failures = mapOf("close" to closeError))

        val result = OperationTransactionExecutor { connection }.transaction {
            ExecuteResult.Success("committed")
        }

        val closeFailure = assertIs<OperationTransactionCompletionException>(
            assertIs<ExecuteResult.Failure>(result).exception,
        )
        assertEquals(true, closeFailure.committed)
        assertSame(closeError, closeFailure.cause)
        assertEquals(listOf("autoCommit:false", "commit", "close"), events)
    }

    @Test
    fun `nested operation transaction is rejected before acquiring another connection`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        var nestedConnectionAttempts = 0
        val outer = OperationTransactionExecutor { connection }
        val inner = OperationTransactionExecutor {
            nestedConnectionAttempts++
            error("nested connection must not be acquired")
        }

        val result = outer.transaction { scope ->
            val nested = inner.transaction { ExecuteResult.Success("nested") }
            assertIs<ExecuteResult.Failure>(nested)
            ExecuteResult.Success("outer")
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, nestedConnectionAttempts)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    private fun connection(
        events: MutableList<String>,
        failures: Map<String, Throwable> = emptyMap(),
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
                "commit", "rollback", "close" -> {
                    events += method.name
                    failures[method.name]?.let { throw it }
                    null
                }
                "isClosed" -> events.contains("close")
                "toString" -> "ProxyConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        }
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            handler,
        ) as Connection
    }
}
