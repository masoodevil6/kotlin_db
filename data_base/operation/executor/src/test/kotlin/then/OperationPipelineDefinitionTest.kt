package gog.my_project.data_base_operation.then

import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationPipelineDefinitionTest {
    @Test
    fun `deferred recipe composes typed stages and propagates output with one shared state`() {
        data class State(var calls: Int = 0)
        data class UserId(val value: Int)
        data class User(val id: UserId)
        data class UserReport(val name: String)

        val recipeCalls = AtomicInteger()
        val stateFactoryCalls = AtomicInteger()
        val contexts = mutableListOf<OperationPipelineStageExecutionContext<State>>()
        lateinit var executionState: State
        val definition = OperationPipelineDefinition.create<State, UserReport>(
            stateFactory = { stateFactoryCalls.incrementAndGet(); State() },
        ) {
            recipeCalls.incrementAndGet()
            start<UserId> { context ->
                contexts += context
                executionState = context.state
                context.state.calls++
                UserId(context.state.calls)
            }.then<UserId, User> { id, context ->
                contexts += context
                assertSame(executionState, context.state)
                context.state.calls++
                User(id)
            }.then<User, UserReport> { user, context ->
                contexts += context
                assertSame(executionState, context.state)
                UserReport("user-${user.id.value}-${context.state.calls}")
            }
        }

        assertEquals(0, recipeCalls.get())
        assertEquals(0, stateFactoryCalls.get())

        val execution = definition.prepareExecution()
        assertEquals(1, recipeCalls.get())
        assertEquals(1, stateFactoryCalls.get())

        val outcome = OperationPipelineRunner.run(execution, scope())

        assertEquals(
            StageOutcome.Completed(UserReport("user-1-2")),
            outcome,
        )
        assertEquals(3, contexts.size)
        assertNotSame(contexts[0], contexts[1])
        assertNotSame(contexts[1], contexts[2])
    }

    @Test
    fun `observed failure propagates stopped through the remaining typed plan`() {
        val failure = IllegalStateException("latched in second stage")
        val calls = mutableListOf<String>()
        val definition = OperationPipelineDefinition.create<Unit, Boolean>(stateFactory = { Unit }) {
            start<Int> {
                calls += "first"
                1
            }.then<Int, String> { _, context ->
                calls += "second"
                context.failed<Unit>(failure)
                "intermediate value"
            }.then<String, Long> { _, _ ->
                calls += "third"
                3L
            }.then<Long, Boolean> { _, _ ->
                calls += "fourth"
                true
            }
        }
        val scope = scope()

        val outcome = OperationPipelineRunner.run(definition.prepareExecution(), scope)

        assertIs<StageOutcome.Stopped>(outcome)
        assertSame(failure, scope.firstFailure)
        assertEquals(listOf("first", "second"), calls)
    }

    @Test
    fun `initial owner failure stops before the first stage`() {
        val failure = IllegalArgumentException("failed before runner entry")
        var stageCalls = 0
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start<String> {
                stageCalls++
                "must not be produced"
            }
        }
        val scope = scope().also { it.recordFailure(failure) }

        val outcome = OperationPipelineRunner.run(definition.prepareExecution(), scope)

        assertIs<StageOutcome.Stopped>(outcome)
        assertSame(failure, scope.firstFailure)
        assertEquals(0, stageCalls)
    }

    @Test
    fun `initial fatal owner error is rethrown with the same identity`() {
        val fatal = AssertionError("fatal before first stage")
        var stageCalls = 0
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start<String> {
                stageCalls++
                "unreachable"
            }
        }
        val scope = scope().also { it.recordFatal(fatal) }

        val thrown = assertFailsWith<AssertionError> {
            OperationPipelineRunner.run(definition.prepareExecution(), scope)
        }

        assertSame(fatal, thrown)
        assertEquals(0, stageCalls)
    }

    @Test
    fun `stage exception expires its context and prevents later stage dispatch`() {
        val failure = IllegalStateException("stage failed")
        var laterStageCalls = 0
        lateinit var savedContext: OperationPipelineStageExecutionContext<Unit>
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start<Int> { 1 }
                .then<Int, String> { _, context ->
                    savedContext = context
                    throw failure
                }.then<String, String> { value, _ ->
                    laterStageCalls++
                    value
                }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            OperationPipelineRunner.run(definition.prepareExecution(), scope())
        }

        assertSame(failure, thrown)
        assertFailsWith<IllegalStateException> { savedContext.state }
        assertEquals(0, laterStageCalls)
    }

    @Test
    fun `state access from another thread is rejected and remembered by the stage context`() {
        var workerFailure: Throwable? = null
        lateinit var context: OperationPipelineStageExecutionContext<String>
        val definition = OperationPipelineDefinition.create<String, String>(stateFactory = { "state" }) {
            start<String> { stageContext ->
                context = stageContext
                val worker = thread {
                    workerFailure = runCatching { stageContext.state }.exceptionOrNull()
                }
                worker.join()
                "returned after catching cross-thread rejection"
            }
        }

        val thrown = assertFailsWith<IllegalStateException> {
            OperationPipelineRunner.run(definition.prepareExecution(), scope())
        }

        assertSame(workerFailure, thrown)
        assertTrue(thrown.message.orEmpty().contains("owner thread"))
        assertFailsWith<IllegalStateException> { context.state }
    }

    @Test
    fun `each preparation creates fresh state and fresh stage contexts`() {
        class State
        val states = mutableListOf<State>()
        val observedStates = mutableListOf<State>()
        val contexts = mutableListOf<OperationPipelineStageExecutionContext<State>>()
        val definition = OperationPipelineDefinition.create<State, Unit>(
            stateFactory = { State().also(states::add) },
        ) {
            start<Unit> { context ->
                contexts += context
                observedStates += context.state
                Unit
            }
        }

        assertTrue(states.isEmpty())
        assertTrue(contexts.isEmpty())

        val first = definition.prepareExecution()
        assertEquals(1, states.size)
        val firstOutcome = OperationPipelineRunner.run(first, scope())
        assertIs<StageOutcome.Completed<Unit>>(firstOutcome)

        val firstContext = contexts.single()
        assertFailsWith<IllegalStateException> { firstContext.state }

        val second = definition.prepareExecution()
        assertEquals(2, states.size)
        val secondOutcome = OperationPipelineRunner.run(second, scope())
        assertIs<StageOutcome.Completed<Unit>>(secondOutcome)

        assertNotSame(states[0], states[1])
        assertEquals(2, contexts.size)
        assertNotSame(contexts[0], contexts[1])
        assertSame(states[0], observedStates[0])
        assertSame(states[1], observedStates[1])
    }

    private fun scope(): OperationExecutionScope {
        val connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> "PipelineDefinitionTestConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "isClosed" -> false
                "getAutoCommit" -> true
                else -> defaultValue(method.returnType)
            }
        } as Connection
        return OperationExecutionScope(connection)
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
