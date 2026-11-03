package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationExecutionExecutorTest {
    @Test
    fun `connection scope owns one connection without transaction control`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val executor = OperationExecutionExecutor { connection }

        val result = executor.connection { scope ->
            assertSame(connection, scope.connection)
            ExecuteResult.Success("done")
        }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `first recorded failure is preserved and connection is closed`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val first = SQLException("first")
        val later = SQLException("later")

        val result = OperationExecutionExecutor { connection }.connection { scope ->
            scope.recordResult(ExecuteResult.Failure(first))
            scope.recordResult(ExecuteResult.Failure(later))
            ExecuteResult.Success("ignored")
        }

        assertSame(first, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `scope rejects operations after callback returns`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        var captured: OperationExecutionScope? = null

        val result = OperationExecutionExecutor { connection }.connection { scope ->
            captured = scope
            ExecuteResult.Success("done")
        }

        assertIs<ExecuteResult.Success<String>>(result)
        val failure = requireNotNull(captured).rejectOperation()
        assertIs<IllegalStateException>(failure)
        assertTrue(failure.message.orEmpty().contains("ended"))
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `cross thread use is rejected and latches failure without early close`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        var workerFailure: Throwable? = null

        val result = OperationExecutionExecutor { connection }.connection { scope ->
            val worker = thread(start = true) {
                workerFailure = assertFailsWith<IllegalStateException> { scope.connection }
            }
            worker.join()
            assertEquals(emptyList(), events)
            ExecuteResult.Success("ignored")
        }

        assertIs<IllegalStateException>(workerFailure)
        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `nested connection scope is rejected without acquiring another connection`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        var acquisitions = 0
        var nestedCallbackInvoked = false
        val executor = OperationExecutionExecutor {
            acquisitions++
            connection
        }

        val result = executor.connection {
            val nested = executor.connection {
                nestedCallbackInvoked = true
                ExecuteResult.Success("unexpected")
            }
            assertIs<ExecuteResult.Failure>(nested)
            ExecuteResult.Success("ignored")
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, acquisitions)
        assertEquals(false, nestedCallbackInvoked)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `close failure is returned without transaction control`() {
        val events = mutableListOf<String>()
        val closeFailure = SQLException("close failed")
        val connection = connection(events, closeFailure = closeFailure)

        val result = OperationExecutionExecutor { connection }.connection {
            ExecuteResult.Success("done")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertIs<IllegalStateException>(failure)
        assertSame(closeFailure, failure.cause)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `callback error remains primary when closing fails`() {
        val events = mutableListOf<String>()
        val callbackError = AssertionError("callback failed")
        val closeFailure = SQLException("close failed")
        val connection = connection(events, closeFailure = closeFailure)

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { connection }.connection<String> {
                throw callbackError
            }
        }

        assertSame(callbackError, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
        assertEquals(listOf("close"), events)
    }

    private fun connection(
        events: MutableList<String>,
        closeFailure: Throwable? = null,
    ): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "close" -> {
                    events += "close"
                    closeFailure?.let { throw it }
                    null
                }
                "setAutoCommit" -> error("Connection Scope must not change autoCommit")
                "commit", "rollback" -> error("Connection Scope must not control a transaction")
                "toString" -> "ProxyConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        },
    ) as Connection
}
