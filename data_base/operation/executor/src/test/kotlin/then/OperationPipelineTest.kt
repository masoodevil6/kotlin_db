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
    fun `typed pipeline carries distinct types in order on one connection`() {
        data class Input(val value: String)
        data class UserId(val value: String)
        data class User(val id: UserId)
        data class UserReport(val summary: String)

        val events = mutableListOf<String>()
        val contexts = mutableListOf<TypedOperationPipelineContext>()
        var acquired = 0

        val pipeline: TypedOperationPipeline<Input, UserReport> =
            TypedOperationPipeline.start<Input, UserId> { input, context ->
                contexts += context
                events += "id:${input.value}"
                UserId(input.value)
            }.then<User> { userId, context ->
                contexts += context
                events += "user:${userId.value}"
                User(userId)
            }.then<UserReport> { user, context ->
                contexts += context
                events += "report:${user.id.value}"
                UserReport("user ${user.id.value}")
            }

        val result = OperationExecutionExecutor {
            acquired++
            connection(events)
        }.executePipeline(pipeline, Input("42"))

        assertEquals(UserReport("user 42"), assertIs<ExecuteResult.Success<UserReport>>(result).result)
        assertEquals(listOf("id:42", "user:42", "report:42", "close"), events)
        assertEquals(1, acquired)
        assertEquals(3, contexts.size)
        assertNotSame(contexts[0], contexts[1])
        assertNotSame(contexts[1], contexts[2])
        assertFalse(TypedOperationPipelineContext::class.java.methods.any { it.name == "next" })
        assertFalse(TypedOperationPipelineContext::class.java.methods.any { it.name == "getConnection" })
    }

    @Test
    fun `typed stage context expires when its stage returns`() {
        lateinit var savedContext: TypedOperationPipelineContext
        val pipeline = TypedOperationPipeline.start<String, String> { input, context ->
            savedContext = context
            input
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }
            .executePipeline(pipeline, "done")

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertFailsWith<IllegalStateException> { savedContext.databasePlatformConfig }
    }

    @Test
    fun `typed stage context rejects use from a non-owner thread`() {
        var workerFailure: Throwable? = null
        val pipeline = TypedOperationPipeline.start<String, String> { input, context ->
            val worker = thread(start = true) {
                workerFailure = runCatching { context.databasePlatformConfig }.exceptionOrNull()
            }
            worker.join()
            input
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }
            .executePipeline(pipeline, "value")

        assertIs<IllegalStateException>(workerFailure)
        assertSame(workerFailure, assertIs<ExecuteResult.Failure>(result).exception)
    }

    @Test
    fun `typed pipeline passes a domain failure value to the next stage`() {
        val original = IllegalArgumentException("domain failure")
        val domainFailure = DomainOutcome.Failed(original)
        var secondStageRan = false
        val pipeline = TypedOperationPipeline.start<String, DomainOutcome> { _, _ -> domainFailure }
            .then<String> { outcome, _ ->
                secondStageRan = true
                assertSame(original, assertIs<DomainOutcome.Failed>(outcome).cause)
                "handled as domain data"
            }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }
            .executePipeline(pipeline, "input")

        assertEquals("handled as domain data", assertIs<ExecuteResult.Success<String>>(result).result)
        assertTrue(secondStageRan)
    }

    @Test
    fun `typed pipeline preserves Operation failure latch while later stages continue`() {
        val primary = SQLException("latched operation failure")
        var laterStageRan = false
        var statementPrepared = false
        val pipeline = TypedOperationPipeline.start<String, String> { _, context ->
            context.failed<Boolean>(primary)
            "continue"
        }.then<String> { prior, context ->
            laterStageRan = true
            assertEquals("continue", prior)
            assertSame(primary, assertIs<ExecuteResult.Failure>(context.executeDdl("SELECT 1")).exception)
            "completed stage"
        }

        val result = OperationExecutionExecutor {
            connection(mutableListOf()) { statementPrepared = true }
        }.executePipeline(pipeline, "input")

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(laterStageRan)
        assertFalse(statementPrepared)
    }

    @Test
    fun `scope typed pipeline records a thrown Exception before caller catches it`() {
        val primary = IllegalStateException("stage exception")
        val events = mutableListOf<String>()
        var scopeFailure: Throwable? = null
        val pipeline = TypedOperationPipeline.start<String, String> { _, _ -> throw primary }

        val result = OperationExecutionExecutor { connection(events) }.connection { scope ->
            scopeFailure = scope.firstFailure
            val caught = assertFailsWith<IllegalStateException> {
                scope.executePipeline(pipeline, "input")
            }
            assertSame(primary, caught)
            assertSame(primary, scope.firstFailure)
            scopeFailure = scope.firstFailure
            ExecuteResult.Success("caller caught the exception")
        }

        assertSame(primary, scopeFailure)
        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `scope typed pipeline returns the latched Failure after a stage returns normally`() {
        val primary = SQLException("latched before return")
        val pipeline = TypedOperationPipeline.start<String, String> { _, context ->
            context.failed<Boolean>(primary)
            "ordinary stage output"
        }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.connection { scope ->
            val pipelineResult = scope.executePipeline(pipeline, "input")
            assertSame(primary, scope.firstFailure)
            assertSame(primary, assertIs<ExecuteResult.Failure>(pipelineResult).exception)
            pipelineResult
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
    }

    @Test
    fun `scope typed pipeline does not replace a prior Failure with its stage output`() {
        val primary = SQLException("prior scope failure")
        val pipeline = TypedOperationPipeline.start<String, String> { _, _ -> "later success value" }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.connection { scope ->
            assertSame(primary, scope.recordFailure(primary))
            val pipelineResult = scope.executePipeline(pipeline, "input")
            assertSame(primary, scope.firstFailure)
            assertSame(primary, assertIs<ExecuteResult.Failure>(pipelineResult).exception)
            pipelineResult
        }

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
    }

    @Test
    fun `typed Scope pipeline keeps Domain Failure as payload without latching it`() {
        val cause = IllegalArgumentException("domain-only failure")
        val payload = DomainOutcome.Failed(cause)
        var contextFailure: Throwable? = null
        val pipeline = TypedOperationPipeline.start<String, DomainOutcome> { _, _ -> payload }
            .then<String> { domainResult, _ ->
                assertSame(cause, assertIs<DomainOutcome.Failed>(domainResult).cause)
                "handled"
            }

        val result = OperationExecutionExecutor { connection(mutableListOf()) }.connection { scope ->
            val pipelineResult = scope.executePipeline(pipeline, "input")
            contextFailure = scope.firstFailure
            assertEquals(null, contextFailure)
            pipelineResult
        }

        assertEquals("handled", assertIs<ExecuteResult.Success<String>>(result).result)
    }

    @Test
    fun `scope typed pipeline records and rethrows the same Error for owner cleanup`() {
        val fatal = AssertionError("scope stage fatal")
        val closeFailure = SQLException("close failed")
        val events = mutableListOf<String>()
        val pipeline = TypedOperationPipeline.start<String, String> { _, _ -> throw fatal }

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { connection(events, closeFailure) }.connection { scope ->
                val caught = assertFailsWith<AssertionError> {
                    scope.executePipeline(pipeline, "input")
                }
                assertSame(fatal, caught)
                assertSame(fatal, scope.fatalError)
                assertFalse(events.contains("close"))
                ExecuteResult.Success("caller caught the Error")
            }
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `typed Executor pipeline keeps one latched Failure and returns it unchanged`() {
        val primary = SQLException("executor pipeline failure")
        val events = mutableListOf<String>()
        val pipeline = TypedOperationPipeline.start<String, String> { _, context ->
            context.failed<Boolean>(primary)
            "returned value"
        }

        val result = OperationExecutionExecutor { connection(events) }
            .executePipeline(pipeline, "input")

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("close"), events)
        assertTrue(primary.suppressed.isEmpty())
    }

    @Test
    fun `typed Executor pipeline leaves a thrown Exception to its Operation owner`() {
        val primary = IllegalArgumentException("executor stage exception")
        val events = mutableListOf<String>()
        val pipeline = TypedOperationPipeline.start<String, String> { _, _ -> throw primary }

        val result = OperationExecutionExecutor { connection(events) }
            .executePipeline(pipeline, "input")

        assertSame(primary, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(primary.suppressed.isEmpty())
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `Scope typed pipeline leaves Connection cleanup and Scope lifecycle to its owner`() {
        val events = mutableListOf<String>()
        lateinit var ownedScope: gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
        val pipeline = TypedOperationPipeline.start<String, String> { input, _ -> input }

        val result = OperationExecutionExecutor { connection(events) }.connection { scope ->
            ownedScope = scope
            val pipelineResult = scope.executePipeline(pipeline, "done")
            assertEquals(null, scope.rejectOperation())
            assertFalse(events.contains("close"))
            pipelineResult
        }

        assertEquals("done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(listOf("close"), events)
        assertTrue(requireNotNull(ownedScope.rejectOperation()).message.orEmpty().contains("scope has ended"))
    }

    @Test
    fun `typed pipeline preserves fatal Error when connection cleanup also fails`() {
        val fatal = AssertionError("typed stage fatal")
        val closeFailure = SQLException("close failed")
        val pipeline = TypedOperationPipeline.start<String, String> { _, _ -> throw fatal }

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor { connection(mutableListOf(), closeFailure) }
                .executePipeline(pipeline, "input")
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
    }

    @Test
    fun `typed scope pipeline runs inside existing SystemLock before release`() {
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
            "typed-pipeline-lock-test",
            config,
            object : IDatabaseConnection {
                override fun build(): Connection = script.connection
            },
            requireNotNull(DatabaseIdentity.from(config)),
        )
        val pipeline = TypedOperationPipeline.start<String, String> { input, _ ->
            script.events += "stage:first"
            input
        }.then<String> { input, _ ->
            script.events += "stage:second"
            "$input:done"
        }

        val result = try {
            OperationExecutionExecutor(databaseContext).connection { scope ->
                scope.withSystemLock(SystemLock.MIGRATION, 1) { lockedScope ->
                    lockedScope.executePipeline(pipeline, "start")
                }
            }
        } finally {
            databaseContext.close()
        }

        assertEquals("start:done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertTrue(script.events.indexOf("execute:acquire") < script.events.indexOf("stage:first"))
        assertTrue(script.events.indexOf("stage:first") < script.events.indexOf("stage:second"))
        assertTrue(script.events.indexOf("stage:second") < script.events.indexOf("execute:release"))
        assertTrue(script.events.indexOf("execute:release") < script.events.indexOf("connection:close"))
    }

    @Test
    fun `typed scope pipeline runs inside existing Transaction lifecycle`() {
        val events = mutableListOf<String>()
        val pipeline = TypedOperationPipeline.start<Int, String> { input, _ ->
            events += "stage:first"
            "id:$input"
        }.then<String> { input, _ ->
            events += "stage:second"
            "$input:done"
        }

        val result = OperationExecutionExecutor { transactionConnection(events) }
            .transaction { scope -> scope.executePipeline(pipeline, 7) }

        assertEquals("id:7:done", assertIs<ExecuteResult.Success<String>>(result).result)
        assertTrue(events.indexOf("autoCommit:false") < events.indexOf("stage:first"))
        assertTrue(events.indexOf("stage:second") < events.indexOf("commit"))
        assertTrue(events.indexOf("commit") < events.indexOf("close"))
        assertFalse(events.contains("rollback"))
    }

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
