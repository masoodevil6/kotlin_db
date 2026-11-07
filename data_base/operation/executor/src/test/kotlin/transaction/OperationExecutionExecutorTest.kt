package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationExecutionExecutorTest {
    @Test
    fun `selected database platform is carried into the operation scope`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val platform = DatabasePlatformConfig(dialect = DialectQuery.MARIA_DB)
        val context = DatabaseContext(
            name = "maria-test",
            config = DatabaseConfig(
                connection = DatabaseConnectionConfig("jdbc:mariadb://localhost", 3306, "test", "user", ""),
                platform = platform,
            ),
            connectionProvider = object : IDatabaseConnection {
                override fun build(): Connection = connection
            },
        )

        val result = OperationExecutionExecutor(context).connection { scope ->
            assertEquals(platform, scope.databasePlatformConfig)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertEquals(listOf("close"), events)
    }

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

    @Test
    fun `earlier operation failure is suppressed on a later fatal callback error`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val earlier = SQLException("first operation failure")
        val fatal = AssertionError("later callback error")

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { connection }.connection<String> { scope ->
                scope.recordFailure(earlier)
                throw fatal
            }
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(earlier), thrown.suppressed.toList())
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `direct Throwable callback failure is returned and connection closes once`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = object : Throwable("direct throwable") {}

        val result = OperationExecutionExecutor { connection }.connection<String> {
            throw failure
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `coordinator lifecycle misuse cannot skip cleanup and coordinator can be reused`() {
        val events = mutableListOf<String>()
        val coordinator = OperationExecutionCoordinator()
        val firstConnection = connection(events)

        val first = OperationExecutionExecutor { firstConnection }.connection(coordinator) { scope ->
            assertFailsWith<IllegalStateException> { coordinator.seal(scope) }
            assertFailsWith<IllegalStateException> { coordinator.finishScope() }
            ExecuteResult.Success("ignored")
        }
        assertIs<ExecuteResult.Failure>(first)
        assertEquals(listOf("close"), events)

        val secondConnection = connection(events)
        val second = OperationExecutionExecutor { secondConnection }.connection(coordinator) {
            ExecuteResult.Success("reused")
        }
        assertEquals("reused", assertIs<ExecuteResult.Success<String>>(second).result)
        assertEquals(listOf("close", "close"), events)
    }

    @Test
    fun `invalid coordinator transitions are rejected`() {
        val coordinator = OperationExecutionCoordinator()
        val scope = OperationExecutionScope(connection(mutableListOf()))

        assertFailsWith<IllegalStateException> { coordinator.activate(scope) }
        assertFailsWith<IllegalStateException> { coordinator.seal(scope) }
        coordinator.finishScope()
    }

    @Test
    fun `active root permit deterministically rejects a transaction until released`() {
        val coordinator = OperationExecutionCoordinator()
        val acquired = CountDownLatch(1)
        val release = CountDownLatch(1)
        var permit: OperationExecutionCoordinator.RootOperationPermit? = null
        val rootWorker = thread(start = true) {
            val admission = coordinator.acquireRootOperation(IllegalStateException("nested root"))
            permit = (admission as OperationExecutionCoordinator.OperationAdmission.Allowed).permit
            acquired.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            requireNotNull(permit).release()
        }
        assertTrue(acquired.await(5, TimeUnit.SECONDS))

        var acquisitions = 0
        val executor = OperationTransactionExecutor {
            acquisitions++
            val transactionEvents = mutableListOf<String>()
            Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "setAutoCommit" -> { transactionEvents += "autoCommit:${args!![0]}"; null }
                    "commit", "rollback", "close" -> { transactionEvents += method.name; null }
                    "getAutoCommit" -> true
                    "isClosed" -> transactionEvents.contains("close")
                    "toString" -> "TransactionProxy"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    else -> null
                }
            } as Connection
        }
        val rejected = executor.transaction(coordinator) { ExecuteResult.Success("unreachable") }
        assertIs<ExecuteResult.Failure>(rejected)
        assertEquals(0, acquisitions)

        release.countDown()
        rootWorker.join()

        val accepted = executor.transaction(coordinator) { ExecuteResult.Success("accepted") }
        assertEquals("accepted", assertIs<ExecuteResult.Success<String>>(accepted).result)
        assertEquals(1, acquisitions)
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
