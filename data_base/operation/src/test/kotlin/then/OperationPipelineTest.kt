package gog.my_project.data_base_operation.then

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.data_base.SystemLock
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationPipelineTest {
    @Test
    fun `stages run in order on one Operation scope and receive distinct expiring contexts`() {
        val events = mutableListOf<String>()
        var acquired = 0
        val contexts = mutableListOf<OperationPipelineStageContext<String>>()
        val pipeline = OperationPipeline.start<String> {
            contexts += this
            next("first")
        }.then { input ->
            assertEquals("first", input)
            contexts += this
            next("second")
        }.then { input ->
            assertEquals("second", input)
            contexts += this
            "complete"
        }

        val result = OperationExecutionExecutor {
            acquired++
            connection(events)
        }.executePipeline(pipeline)

        assertEquals("complete", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(1, acquired)
        assertEquals(listOf("close"), events)
        assertEquals(3, contexts.size)
        assertNotSame(contexts[0], contexts[1])
        assertNotSame(contexts[1], contexts[2])
        assertFailsWith<IllegalStateException> { contexts.first().executeDdl("SELECT 1") }
        assertFalse(OperationPipelineStageContext::class.java.methods.any { it.name == "getConnection" })
    }

    @Test
    fun `stage without next completes pipeline without running later stages`() {
        var laterStageRan = false
        val pipeline = OperationPipeline.start { "finished here" }
            .then { input ->
                laterStageRan = true
                input
            }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.executePipeline(pipeline)

        assertEquals("finished here", assertIs<ExecuteResult.Success<String>>(result).result)
        assertFalse(laterStageRan)
    }

    @Test
    fun `domain failure remains a payload and can be passed to the next stage`() {
        val original = IllegalArgumentException("domain failure")
        var secondStageRan = false
        val failurePayload = DomainOutcome.Failed(original)
        val pipeline = OperationPipeline.start<DomainOutcome> {
            next(failurePayload)
            failurePayload
        }.then { input ->
            secondStageRan = true
            assertSame(original, assertIs<DomainOutcome.Failed>(input).cause)
            input
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.executePipeline(pipeline)

        assertSame(failurePayload, assertIs<ExecuteResult.Success<DomainOutcome>>(result).result)
        assertTrue(secondStageRan)
    }

    @Test
    fun `next remains available after a scope failure while ordinary operations stay latched`() {
        val primary = SQLException("first failure")
        var secondStageRan = false
        var statementPrepared = false
        val pipeline = OperationPipeline.start<String> {
            failed<Boolean>(primary)
            next("continue control flow")
            "first stage"
        }.then { input ->
            secondStageRan = true
            assertEquals("continue control flow", input)
            val operationResult = executeDdl("SELECT 1")
            assertSame(primary, assertIs<ExecuteResult.Failure>(operationResult).exception)
            "second stage"
        }

        val result = OperationExecutionExecutor {
            connection(mutableListOf()) { statementPrepared = true }
        }.executePipeline(pipeline)

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(secondStageRan)
        assertFalse(statementPrepared)
    }

    @Test
    fun `next can only be called once even if the second call is caught`() {
        var secondStageRan = false
        var caught: IllegalStateException? = null
        val pipeline = OperationPipeline.start<String> {
            next("first request")
            caught = assertFailsWith<IllegalStateException> { next("second request") }
            "returned"
        }.then { input ->
            secondStageRan = true
            input
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.executePipeline(pipeline)

        assertSame(requireNotNull(caught), assertIs<ExecuteResult.Failure>(result).exception)
        assertFalse(secondStageRan)
    }

    @Test
    fun `NEW is rejected explicitly in Phase 1`() {
        var laterStageRan = false
        val pipeline = OperationPipeline.start<String> {
            next("not accepted", ConnectionDecision.NEW)
            "unreachable"
        }.then { input ->
            laterStageRan = true
            input
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.executePipeline(pipeline)

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertIs<IllegalStateException>(failure)
        assertTrue(failure.message.orEmpty().contains("NEW is not supported"))
        assertFalse(laterStageRan)
    }

    @Test
    fun `context rejects calls from a thread other than the Stage owner`() {
        var workerFailure: Throwable? = null
        var laterStageRan = false
        val pipeline = OperationPipeline.start<String> {
            val worker = thread(start = true) {
                workerFailure = runCatching { next("cross-thread") }.exceptionOrNull()
            }
            worker.join()
            "stage returned"
        }.then { input ->
            laterStageRan = true
            input
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.executePipeline(pipeline)

        assertIs<IllegalStateException>(workerFailure)
        assertSame(workerFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertFalse(laterStageRan)
    }

    @Test
    fun `fatal Error reference survives Pipeline and Connection cleanup failure`() {
        val fatal = AssertionError("fatal stage error")
        val closeFailure = SQLException("close failed")

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { connection(mutableListOf(), closeFailure) }
                .executePipeline(OperationPipeline.start { throw fatal })
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
    }

    @Test
    fun `scope Pipeline completes inside the existing SystemLock callback before release`() {
        val script = LockConnectionScript()
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://db.internal",
                dbPort = 3306,
                dbName = "catalog",
                dbUserName = "user",
                dbPassword = "secret",
            ),
            platform = DatabasePlatformConfig(DialectQuery.MY_SQL),
        )
        val databaseContext = DatabaseContext(
            "pipeline-lock-test",
            config,
            object : IDatabaseConnection {
                override fun build(): Connection = script.connection
            },
            requireNotNull(DatabaseIdentity.from(config)),
        )
        val pipeline = OperationPipeline.start<String> {
            script.events += "stage:first"
            next("continue")
            "first"
        }.then { input ->
            assertEquals("continue", input)
            script.events += "stage:second"
            "done"
        }

        val result = try {
            OperationExecutionExecutor(databaseContext).connection { scope ->
                scope.withSystemLock(SystemLock.MIGRATION, 1) { lockedScope ->
                    lockedScope.executePipeline(pipeline)
                }
            }
        } finally {
            databaseContext.close()
        }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertTrue(script.events.indexOf("execute:acquire") < script.events.indexOf("stage:first"))
        assertTrue(script.events.indexOf("stage:first") < script.events.indexOf("stage:second"))
        assertTrue(script.events.indexOf("stage:second") < script.events.indexOf("execute:release"))
        assertTrue(script.events.indexOf("execute:release") < script.events.indexOf("connection:close"))
    }

    @Test
    fun `scope Pipeline can run inside an existing Transaction without adding transaction control`() {
        val events = mutableListOf<String>()
        val pipeline = OperationPipeline.start<String> {
            events += "stage:first"
            next("continue")
        }.then { input ->
            assertEquals("continue", input)
            events += "stage:second"
            "done"
        }

        val result = OperationExecutionExecutor { transactionConnection(events) }
            .transaction { scope -> scope.executePipeline(pipeline) }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertTrue(events.indexOf("autoCommit:false") < events.indexOf("stage:first"))
        assertTrue(events.indexOf("stage:second") < events.indexOf("commit"))
        assertTrue(events.indexOf("commit") < events.indexOf("close"))
        assertFalse(events.contains("rollback"))
    }

    private fun connection(
        events: MutableList<String>,
        closeFailure: SQLException? = null,
        onPrepareStatement: () -> Unit = {},
    ): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "close" -> {
                events += "close"
                closeFailure?.let { throw it }
                null
            }
            "prepareStatement" -> {
                onPrepareStatement()
                throw AssertionError("No statement should be prepared in this test")
            }
            "setAutoCommit" -> error("Connection Scope must not change autoCommit")
            "commit", "rollback" -> error("Connection Scope must not control a transaction")
            "toString" -> "PipelineTestConnection"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            else -> defaultValue(method.returnType)
        }
    } as Connection

    private fun transactionConnection(events: MutableList<String>): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "setAutoCommit" -> { events += "autoCommit:${args!![0]}"; null }
            "commit", "rollback" -> { events += method.name; null }
            "close" -> { events += "close"; null }
            "getAutoCommit" -> true
            "isClosed" -> events.contains("close")
            "toString" -> "PipelineTransactionConnection"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            else -> defaultValue(method.returnType)
        }
    } as Connection

    private class LockConnectionScript {
        val events = mutableListOf<String>()

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val sql = args!![0] as String
                    val kind = if (sql.contains("GET_LOCK")) "acquire" else "release"
                    events += "prepare:$kind"
                    preparedStatement(kind)
                }
                "close" -> { events += "connection:close"; null }
                "isClosed" -> false
                "toString" -> "PipelineLockConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun preparedStatement(kind: String): PreparedStatement = Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "setObject", "close" -> null
                "executeQuery" -> {
                    events += "execute:$kind"
                    resultSet(kind)
                }
                "toString" -> "PipelineLockStatement($kind)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        private fun resultSet(kind: String): ResultSet {
            var beforeFirst = true
            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "next" -> if (beforeFirst) { beforeFirst = false; true } else false
                    "getInt" -> {
                        val expectedLabel = if (kind == "acquire") "acquired" else "released"
                        require(args!![0] == expectedLabel)
                        1
                    }
                    "wasNull" -> false
                    "close" -> null
                    "toString" -> "PipelineLockResultSet($kind)"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.get(0)
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }
    }

    private sealed interface DomainOutcome {
        data class Failed(val cause: Throwable) : DomainOutcome
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
    else -> null
}
