package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import kotlin.concurrent.thread
import java.lang.reflect.UndeclaredThrowableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    @Test
    fun `public end request latches failure and cannot bypass transaction cleanup`() {
        val events = mutableListOf<String>()
        val connection = connection(events)

        val result = OperationTransactionExecutor { connection }.transaction { scope ->
            scope.end()
            ExecuteResult.Success("must roll back")
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `earlier and later failures remain suppressed when callback Error takes priority`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val first = SQLException("first failure")
        val later = IllegalArgumentException("later failure")
        val fatal = AssertionError("fatal callback error")

        val thrown = assertFailsWith<AssertionError> {
            OperationTransactionExecutor { connection }.transaction<String> { scope ->
                scope.recordFailure(first)
                scope.recordFailure(later)
                throw fatal
            }
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(first, later), thrown.suppressed.toList())
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `direct Throwable callback failure rolls back and closes once`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = object : Throwable("direct throwable") {}

        val result = OperationTransactionExecutor { connection }.transaction<String> {
            throw failure
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `transaction setup failure closes without rollback because no transaction started`() {
        val events = mutableListOf<String>()
        val failure = SQLException("transaction setup failed")
        val connection = connection(events, failures = mapOf("setAutoCommit" to failure))

        val result = OperationTransactionExecutor { connection }.transaction<String> {
            ExecuteResult.Success("unreachable")
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "close"), events)
    }

    @Test
    fun `coordinator is reusable after success and rollback`() {
        val coordinator = OperationExecutionCoordinator()
        val events = mutableListOf<String>()
        val executor = OperationTransactionExecutor { connection(events) }

        val failed = executor.transaction(coordinator) { scope ->
            val failure = SQLException("rollback this transaction")
            scope.recordFailure(failure)
            ExecuteResult.Success("ignored")
        }
        assertIs<ExecuteResult.Failure>(failed)

        val succeeded = executor.transaction(coordinator) { ExecuteResult.Success("next transaction") }
        assertEquals("next transaction", assertIs<ExecuteResult.Success<String>>(succeeded).result)
        assertEquals(
            listOf(
                "autoCommit:false", "rollback", "close",
                "autoCommit:false", "commit", "close",
            ),
            events,
        )
    }

    @Test
    fun `consumer cannot activate an arbitrary scope during setup`() {
        val events = mutableListOf<String>()
        val coordinator = OperationExecutionCoordinator()
        val connection = connection(events)
        val executor = OperationTransactionExecutor({
            val arbitrary = OperationExecutionScope(connection)
            assertFailsWith<IllegalStateException> { coordinator.activate(arbitrary) }
            connection
        }, gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig())

        val result = executor.transaction(coordinator) { ExecuteResult.Success("must roll back") }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `premature public seal cannot skip transaction rollback`() {
        val events = mutableListOf<String>()
        val coordinator = OperationExecutionCoordinator()
        val connection = connection(events)

        val result = OperationTransactionExecutor { connection }.transaction(coordinator) { scope ->
            assertFailsWith<IllegalStateException> { coordinator.seal(scope) }
            ExecuteResult.Success("must roll back")
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Suppress("DEPRECATION")
    @Test
    fun `foreign scope seal during active transaction latches failure and coordinator is reusable`() {
        val events = mutableListOf<String>()
        val coordinator = OperationExecutionCoordinator()
        val firstConnection = connection(events)
        val secondConnection = connection(events)
        var acquisitions = 0
        val executor = OperationTransactionExecutor {
            if (acquisitions++ == 0) firstConnection else secondConnection
        }

        val failed = executor.transaction(coordinator) { _ ->
            val foreignScope = OperationExecutionScope(firstConnection)
            assertFailsWith<IllegalStateException> { coordinator.seal(foreignScope) }
            ExecuteResult.Success("must roll back")
        }

        assertIs<ExecuteResult.Failure>(failed)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)

        val reused = executor.transaction(coordinator) { ExecuteResult.Success("reused") }

        assertEquals("reused", assertIs<ExecuteResult.Success<String>>(reused).result)
        assertEquals(
            listOf("autoCommit:false", "rollback", "close", "autoCommit:false", "commit", "close"),
            events,
        )
    }

    @Test
    fun `connection scope runs sequential child transactions on the same connection and platform`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val platform = DatabasePlatformConfig(dialect = DialectQuery.MARIA_DB)
        var acquisitions = 0
        val context = DatabaseContext(
            name = "child-transaction-test",
            config = DatabaseConfig(
                connection = DatabaseConnectionConfig("jdbc:test", 0, "test", "test", ""),
                platform = platform,
            ),
            connectionProvider = object : IDatabaseConnection {
                override fun build(): Connection {
                    acquisitions++
                    return connection
                }
            },
        )
        val executor = OperationExecutionExecutor(context)

        val result = executor.connection { connectionScope ->
            assertSame(platform, connectionScope.databasePlatformConfig)
            repeat(3) { index ->
                val childResult = connectionScope.transaction { transactionScope ->
                    assertSame(connection, transactionScope.connection)
                    assertSame(platform, transactionScope.databasePlatformConfig)
                    ExecuteResult.Success(index)
                }
                assertEquals(index, assertIs<ExecuteResult.Success<Int>>(childResult).result)
                assertNull(connectionScope.firstFailure)
            }
            ExecuteResult.Success("connection remains parent-owned")
        }

        assertEquals("connection remains parent-owned", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(1, acquisitions)
        assertEquals(
            listOf(
                "autoCommit:false", "commit", "autoCommit:true",
                "autoCommit:false", "commit", "autoCommit:true",
                "autoCommit:false", "commit", "autoCommit:true", "close",
            ),
            events,
        )
    }

    @Test
    fun `child failure reaches parent only after rollback and state restoration`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val failure = SQLException("child operation failed")

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            val childResult = parentScope.transaction { childScope ->
                childScope.recordResult(ExecuteResult.Failure(failure))
                assertNull(parentScope.firstFailure)
                ExecuteResult.Success("ignored")
            }
            assertSame(failure, assertIs<ExecuteResult.Failure>(childResult).exception)
            assertSame(failure, parentScope.firstFailure)
            ExecuteResult.Success("parent callback handled the result")
        }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "autoCommit:true", "close"), events)
    }

    @Test
    fun `failure in a later child transaction does not undo an earlier commit`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val laterFailure = SQLException("second transaction failed")

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            val first = parentScope.transaction { ExecuteResult.Success("first committed") }
            assertEquals("first committed", assertIs<ExecuteResult.Success<String>>(first).result)

            val second = parentScope.transaction { ExecuteResult.Failure(laterFailure) }
            assertSame(laterFailure, assertIs<ExecuteResult.Failure>(second).exception)
            ExecuteResult.Success("parent cannot erase child failure")
        }

        assertSame(laterFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(
            listOf("autoCommit:false", "commit", "autoCommit:true", "autoCommit:false", "rollback", "autoCommit:true", "close"),
            events,
        )
    }

    @Test
    fun `query and schema execution inside child use the parent connection`() {
        val events = mutableListOf<String>()
        val connection = operationConnection(events)
        var acquisitions = 0

        val result = OperationExecutionExecutor {
            acquisitions++
            connection
        }.connection { parentScope ->
            parentScope.transaction { childScope ->
                assertEquals(ExecuteResult.Success(true), childScope.executeDdl("CREATE TABLE sample (id INT)"))
                val selected = childScope.executeRead("SELECT 17 AS id") { rows ->
                    check(rows.next())
                    rows.getInt("id")
                }
                assertEquals(ExecuteResult.Success(17), selected)
                ExecuteResult.Success("done")
            }
        }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(1, acquisitions)
        assertEquals(
            listOf(
                "autoCommit:false",
                "prepare:CREATE TABLE sample (id INT)", "execute:CREATE TABLE sample (id INT)", "statement.close",
                "prepare:SELECT 17 AS id", "executeQuery:SELECT 17 AS id", "result.close", "statement.close",
                "commit", "autoCommit:true", "close",
            ),
            events,
        )
    }

    @Test
    fun `parent operations are rejected while child transaction is active`() {
        val events = mutableListOf<String>()
        val connection = operationConnection(events)

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            parentScope.transaction { childScope ->
                val rejected = parentScope.executeDdl("CREATE TABLE must_not_run (id INT)")
                assertIs<ExecuteResult.Failure>(rejected)
                assertTrue(childScope.firstFailure?.message.orEmpty().contains("Parent Connection Scope"))
                ExecuteResult.Success("must roll back")
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("autoCommit:false", "rollback", "autoCommit:true", "close"), events)
    }

    @Test
    fun `parent operation from another thread fails the active child transaction`() {
        val events = mutableListOf<String>()
        val connection = operationConnection(events)

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            parentScope.transaction { childScope ->
                var rejected: ExecuteResult<Boolean>? = null
                val worker = thread(start = true) {
                    rejected = parentScope.executeDdl("CREATE TABLE must_not_run (id INT)")
                }
                worker.join()
                assertIs<ExecuteResult.Failure>(rejected)
                assertTrue(childScope.firstFailure != null)
                ExecuteResult.Success("must roll back")
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("autoCommit:false", "rollback", "autoCommit:true", "close"), events)
    }

    @Test
    fun `transaction scopes cannot be nested and expired children cannot outlive parent`() {
        val events = mutableListOf<String>()
        val connection = connection(events)
        val nestedResult = OperationTransactionExecutor { connection }.transaction { transactionScope ->
            val nested = transactionScope.transaction { ExecuteResult.Success("unreachable") }
            assertIs<ExecuteResult.Failure>(nested)
            ExecuteResult.Success("must roll back")
        }
        assertIs<ExecuteResult.Failure>(nestedResult)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)

        val parentConnectionEvents = mutableListOf<String>()
        val parentConnection = connection(parentConnectionEvents)
        var capturedChild: OperationExecutionScope? = null
        val connectionResult = OperationExecutionExecutor { parentConnection }.connection { parentScope ->
            val childResult = parentScope.transaction { childScope ->
                capturedChild = childScope
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Success<Unit>>(childResult)
            ExecuteResult.Success(Unit)
        }
        assertIs<ExecuteResult.Success<Unit>>(connectionResult)

        val afterEnd = requireNotNull(capturedChild).executeDdl("CREATE TABLE after_end (id INT)")
        assertIs<ExecuteResult.Failure>(afterEnd)
        assertEquals(listOf("autoCommit:false", "commit", "autoCommit:true", "close"), parentConnectionEvents)

        var capturedParent: OperationExecutionScope? = null
        var invalidParentCallbackInvoked = false
        val expiredParentEvents = mutableListOf<String>()
        val expiredParentConnection = connection(expiredParentEvents)
        val expiredParentResult = OperationExecutionExecutor { expiredParentConnection }.connection { parentScope ->
            capturedParent = parentScope
            ExecuteResult.Success(Unit)
        }
        assertIs<ExecuteResult.Success<Unit>>(expiredParentResult)

        val childFromExpiredParent = requireNotNull(capturedParent).transaction {
            invalidParentCallbackInvoked = true
            ExecuteResult.Success(Unit)
        }
        assertIs<ExecuteResult.Failure>(childFromExpiredParent)
        assertEquals(false, invalidParentCallbackInvoked)
        assertEquals(listOf("close"), expiredParentEvents)
    }

    @Test
    fun `borrowed transaction setup failure rolls back restores autoCommit and leaves connection open`() {
        val events = mutableListOf<String>()
        val beginFailure = SQLException("begin failed")
        val connection = connection(events, failures = mapOf("setAutoCommit:false" to beginFailure))

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            val child = parentScope.transaction { ExecuteResult.Success(Unit) }
            assertSame(beginFailure, assertIs<ExecuteResult.Failure>(child).exception)
            ExecuteResult.Success(Unit)
        }

        assertSame(beginFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "autoCommit:true", "close"), events)
    }

    @Test
    fun `borrowed transaction rejects a parent connection already in a transaction`() {
        val events = mutableListOf<String>()
        val connection = connection(events, initialAutoCommit = false)

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            val child = parentScope.transaction { ExecuteResult.Success("unreachable") }
            val failure = assertIs<ExecuteResult.Failure>(child).exception
            assertTrue(failure.message.orEmpty().contains("already transactional"))
            ExecuteResult.Success("must be reported as failure")
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `borrowed transaction commit and rollback failures preserve outcomes without closing early`() {
        val commitEvents = mutableListOf<String>()
        val commitFailure = SQLException("commit failed")
        val commitConnection = connection(commitEvents, failures = mapOf("commit" to commitFailure))
        val commitResult = OperationExecutionExecutor { commitConnection }.connection { parentScope ->
            parentScope.transaction { ExecuteResult.Success("value") }
        }
        val commitReported = assertIs<OperationTransactionCommitException>(
            assertIs<ExecuteResult.Failure>(commitResult).exception,
        )
        assertSame(commitFailure, commitReported.cause)
        assertEquals(listOf("autoCommit:false", "commit", "rollback", "autoCommit:true", "close"), commitEvents)

        val rollbackEvents = mutableListOf<String>()
        val callbackFailure = SQLException("callback failed")
        val rollbackFailure = SQLException("rollback failed")
        val rollbackConnection = connection(rollbackEvents, failures = mapOf("rollback" to rollbackFailure))
        val rollbackResult = OperationExecutionExecutor { rollbackConnection }.connection { parentScope ->
            parentScope.transaction { ExecuteResult.Failure(callbackFailure) }
        }
        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(rollbackResult).exception)
        assertEquals(listOf(rollbackFailure), callbackFailure.suppressed.toList())
        assertEquals(listOf("autoCommit:false", "rollback", "autoCommit:true", "close"), rollbackEvents)
    }

    @Test
    fun `autoCommit restoration failure is preserved after rollback`() {
        val events = mutableListOf<String>()
        val callbackFailure = SQLException("callback failed")
        val restoreFailure = SQLException("restore failed")
        val connection = connection(events, failures = mapOf("setAutoCommit:true" to restoreFailure))

        val result = OperationExecutionExecutor { connection }.connection { parentScope ->
            parentScope.transaction { ExecuteResult.Failure(callbackFailure) }
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf(restoreFailure), callbackFailure.suppressed.toList())
        assertEquals(listOf("autoCommit:false", "rollback", "autoCommit:true", "close"), events)
    }

    @Test
    fun `rollback cleanup preserves direct Throwable as suppressed`() {
        val events = mutableListOf<String>()
        val original = SQLException("original")
        val cleanup = object : Throwable("cleanup") {}
        val connection = connection(events, failures = mapOf("rollback" to cleanup))

        val result = OperationTransactionExecutor { connection }.transaction<String> {
            ExecuteResult.Failure(original)
        }

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        val wrappedCleanup = assertIs<UndeclaredThrowableException>(original.suppressed.single())
        assertSame(cleanup, wrappedCleanup.undeclaredThrowable)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    private fun connection(
        events: MutableList<String>,
        failures: Map<String, Throwable> = emptyMap(),
        initialAutoCommit: Boolean = true,
    ): Connection {
        var autoCommit = initialAutoCommit
        val handler = InvocationHandler { proxy, method, args ->
            when (method.name) {
                "setAutoCommit" -> {
                    autoCommit = args!![0] as Boolean
                    events += "autoCommit:$autoCommit"
                    (failures["setAutoCommit:$autoCommit"] ?: failures["setAutoCommit"])?.let { throw it }
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

    private fun operationConnection(events: MutableList<String>): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "setAutoCommit" -> {
                    events += "autoCommit:${args!![0]}"
                    null
                }
                "getAutoCommit" -> true
                "commit", "rollback", "close" -> {
                    events += method.name
                    null
                }
                "prepareStatement" -> {
                    val sql = args!![0] as String
                    events += "prepare:$sql"
                    operationStatement(sql, events)
                }
                "isClosed" -> events.contains("close")
                "toString" -> "OperationConnectionProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        },
    ) as Connection

    private fun operationStatement(sql: String, events: MutableList<String>): PreparedStatement =
        Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
            InvocationHandler { proxy, method, args ->
                when (method.name) {
                    "execute" -> {
                        events += "execute:$sql"
                        true
                    }
                    "executeQuery" -> {
                        events += "executeQuery:$sql"
                        operationResultSet(events)
                    }
                    "close" -> {
                        events += "statement.close"
                        null
                    }
                    "toString" -> "OperationStatementProxy"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    else -> when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        else -> null
                    }
                }
            },
        ) as PreparedStatement

    private fun operationResultSet(events: MutableList<String>): ResultSet {
        var consumed = false
        val metadata = Proxy.newProxyInstance(
            ResultSetMetaData::class.java.classLoader,
            arrayOf(ResultSetMetaData::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getColumnCount" -> 1
                "getColumnLabel", "getColumnName" -> "id"
                else -> null
            }
        } as ResultSetMetaData
        return Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getMetaData" -> metadata
                "next" -> if (!consumed) { consumed = true; true } else false
                "getInt" -> 17
                "wasNull" -> false
                "close" -> {
                    events += "result.close"
                    null
                }
                "toString" -> "OperationResultSetProxy"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    else -> null
                }
            }
        } as ResultSet
    }
}
