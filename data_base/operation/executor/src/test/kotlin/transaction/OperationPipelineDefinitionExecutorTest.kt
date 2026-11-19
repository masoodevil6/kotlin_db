package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.then.OperationPipelineDefinition
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class OperationPipelineDefinitionExecutorTest {
    @Test
    fun `connection definition runs typed stages with shared state and fresh state per execution`() {
        class State(var stages: Int = 0)

        val stateFactoryCalls = AtomicInteger()
        val recipeCalls = AtomicInteger()
        val stageStates = mutableListOf<State>()
        val lifecycle = mutableListOf<String>()
        val connections = AtomicInteger()
        val executor = OperationExecutionExecutor {
            recordingConnection(lifecycle, "connection-${connections.incrementAndGet()}")
        }
        val coordinator = OperationExecutionCoordinator()
        val definition = OperationPipelineDefinition.create<State, String>(
            stateFactory = { stateFactoryCalls.incrementAndGet(); State() },
        ) {
            recipeCalls.incrementAndGet()
            start<Int> { context ->
                stageStates += context.state
                context.state.stages++
                20
            }.then<Int, String> { input, context ->
                stageStates += context.state
                assertSame(stageStates[stageStates.lastIndex - 1], context.state)
                context.state.stages++
                "${input + context.state.stages}"
            }
        }

        val first = executor.executeConnectionDefinition(coordinator, definition)
        val second = executor.executeConnectionDefinition(coordinator, definition)

        assertEquals("22", assertIs<ExecuteResult.Success<String>>(first).result)
        assertEquals("22", assertIs<ExecuteResult.Success<String>>(second).result)
        assertEquals(2, recipeCalls.get())
        assertEquals(2, stateFactoryCalls.get())
        assertEquals(4, stageStates.size)
        assertSame(stageStates[0], stageStates[1])
        assertSame(stageStates[2], stageStates[3])
        assertNotSame(stageStates[0], stageStates[2])
        assertEquals(listOf("connection-1:close", "connection-2:close"), lifecycle)
    }

    @Test
    fun `transaction definition runs typed stages and commits once`() {
        class State(var total: Int = 0)

        val lifecycle = mutableListOf<String>()
        val stageStates = mutableListOf<State>()
        val definition = OperationPipelineDefinition.create<State, String>(
            stateFactory = { State() },
        ) {
            start<Int> { context ->
                stageStates += context.state
                context.state.total = 4
                3
            }.then<Int, String> { input, context ->
                stageStates += context.state
                context.state.total += input
                "total-${context.state.total}"
            }
        }

        val result = OperationExecutionExecutor {
            recordingConnection(lifecycle, "transaction")
        }.executeTransactionDefinition(definition)

        assertEquals("total-7", assertIs<ExecuteResult.Success<String>>(result).result)
        assertSame(stageStates[0], stageStates[1])
        assertEquals(listOf("transaction:autoCommit:false", "transaction:commit", "transaction:close"), lifecycle)
    }

    @Test
    fun `recipe failure is returned before state creation or connection acquisition`() {
        val recipeFailure = IllegalStateException("invalid recipe")
        val stateFactoryCalls = AtomicInteger()
        val acquisitions = AtomicInteger()
        val definition = OperationPipelineDefinition.create<Unit, String>(
            stateFactory = { stateFactoryCalls.incrementAndGet(); Unit },
        ) {
            throw recipeFailure
        }

        val result = OperationExecutionExecutor {
            acquisitions.incrementAndGet()
            recordingConnection(mutableListOf(), "unused")
        }.executeConnectionDefinition(definition)

        assertSame(recipeFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, stateFactoryCalls.get())
        assertEquals(0, acquisitions.get())
    }

    @Test
    fun `preparation failure prevents first stage and resource acquisition`() {
        val acquisitions = AtomicInteger()
        val stageCalls = AtomicInteger()
        val executor = OperationExecutionExecutor {
            acquisitions.incrementAndGet()
            recordingConnection(mutableListOf(), "unused")
        }
        val coordinator = OperationExecutionCoordinator()
        var nestedResult: ExecuteResult<Unit>? = null
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            nestedResult = executor.connection(coordinator) { ExecuteResult.Success(Unit) }
            start<String> {
                stageCalls.incrementAndGet()
                "must not run"
            }
        }

        val result = executor.executeConnectionDefinition(coordinator, definition)

        assertIs<ExecuteResult.Failure>(nestedResult)
        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, stageCalls.get())
        assertEquals(0, acquisitions.get())
    }

    @Test
    fun `ordinary failure in a non final connection stage stops later stages`() {
        val failure = IllegalStateException("stage operation failed")
        val lifecycle = mutableListOf<String>()
        val laterStageCalls = AtomicInteger()
        val definition = OperationPipelineDefinition.create<Unit, Long>(stateFactory = { Unit }) {
            start<Int> { 1 }
                .then<Int, String> { _, context ->
                    context.failed<Unit>(failure)
                    "latched failure"
                }.then<String, Long> { _, _ ->
                    laterStageCalls.incrementAndGet()
                    9L
                }
        }

        val result = OperationExecutionExecutor {
            recordingConnection(lifecycle, "connection")
        }.executeConnectionDefinition(definition)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, laterStageCalls.get())
        assertEquals(listOf("connection:close"), lifecycle)
    }

    @Test
    fun `final connection stage failure is selected instead of its returned value`() {
        val failure = IllegalStateException("final connection failure")
        val lifecycle = mutableListOf<String>()
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start<Int> { 1 }.then<Int, String> { _, context ->
                context.failed<String>(failure)
                "nominal success"
            }
        }

        val result = OperationExecutionExecutor {
            recordingConnection(lifecycle, "connection")
        }.executeConnectionDefinition(definition)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("connection:close"), lifecycle)
    }

    @Test
    fun `final transaction stage failure prevents commit and rolls back`() {
        val failure = IllegalStateException("final transaction failure")
        val lifecycle = mutableListOf<String>()
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start<Int> { 1 }.then<Int, String> { _, context ->
                context.failed<String>(failure)
                "nominal success"
            }
        }

        val result = OperationExecutionExecutor {
            recordingConnection(lifecycle, "transaction")
        }.executeTransactionDefinition(definition)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("transaction:autoCommit:false", "transaction:rollback", "transaction:close"), lifecycle)
    }

    @Test
    fun `fatal stage errors keep identity and operation cleanup for both lifecycles`() {
        val connectionLifecycle = mutableListOf<String>()
        val connectionFatal = AssertionError("connection pipeline fatal")
        val connectionDefinition = errorDefinition(connectionFatal)
        val connectionThrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor {
                recordingConnection(connectionLifecycle, "connection")
            }.executeConnectionDefinition(connectionDefinition)
        }

        val transactionLifecycle = mutableListOf<String>()
        val transactionFatal = AssertionError("transaction pipeline fatal")
        val transactionDefinition = errorDefinition(transactionFatal)
        val transactionThrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor {
                recordingConnection(transactionLifecycle, "transaction")
            }.executeTransactionDefinition(transactionDefinition)
        }

        assertSame(connectionFatal, connectionThrown)
        assertSame(transactionFatal, transactionThrown)
        assertEquals(listOf("connection:close"), connectionLifecycle)
        assertEquals(listOf("transaction:autoCommit:false", "transaction:rollback", "transaction:close"), transactionLifecycle)
    }

    @Test
    fun `ordinary stage exceptions keep identity and follow connection and transaction cleanup`() {
        val connectionFailure = IllegalStateException("connection stage exception")
        val connectionLifecycle = mutableListOf<String>()
        val connectionResult = OperationExecutionExecutor {
            recordingConnection(connectionLifecycle, "connection")
        }.executeConnectionDefinition(exceptionDefinition(connectionFailure))

        val transactionFailure = IllegalStateException("transaction stage exception")
        val transactionLifecycle = mutableListOf<String>()
        val transactionResult = OperationExecutionExecutor {
            recordingConnection(transactionLifecycle, "transaction")
        }.executeTransactionDefinition(exceptionDefinition(transactionFailure))

        assertSame(connectionFailure, assertIs<ExecuteResult.Failure>(connectionResult).exception)
        assertSame(transactionFailure, assertIs<ExecuteResult.Failure>(transactionResult).exception)
        assertEquals(listOf("connection:close"), connectionLifecycle)
        assertEquals(listOf("transaction:autoCommit:false", "transaction:rollback", "transaction:close"), transactionLifecycle)
    }

    private fun errorDefinition(error: Error) = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
        start<String> { throw error }
    }

    private fun exceptionDefinition(exception: Exception) = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
        start<String> { throw exception }
    }

    private fun recordingConnection(events: MutableList<String>, name: String): Connection {
        var autoCommit = true
        var closed = false
        return Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "getAutoCommit" -> autoCommit
                "setAutoCommit" -> {
                    autoCommit = args?.firstOrNull() as Boolean
                    events += "$name:autoCommit:$autoCommit"
                    null
                }
                "commit", "rollback" -> {
                    events += "$name:${method.name}"
                    null
                }
                "close" -> {
                    closed = true
                    events += "$name:close"
                    null
                }
                "isClosed" -> closed
                "toString" -> "PipelineTestConnection($name)"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                else -> defaultValue(method.returnType)
            }
        } as Connection
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
