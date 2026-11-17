package gog.my_project.data_base_operation.then

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TypedContinuationPipelineTest {
    @Test
    fun `connection stages pass typed values in order on one owned connection`() {
        val probe = ConnectionProbe()
        val calls = mutableListOf<String>()
        val contexts = mutableListOf<Any>()
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            calls += "load"
            contexts += context
            context.next("loaded").connection(ConnectionDecision.REUSE)
        }.then<Int> { value, context ->
            calls += "validate:$value"
            contexts += context
            context.next(value.length).connection(ConnectionDecision.REUSE)
        }.then<String> { length, context ->
            calls += "execute:$length"
            contexts += context
            "history:$length"
        }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        val completed = assertIs<ExecuteResult.Success<PipelineCompletion<PipelineCompletion<String, Int>, String>>>(result)
        assertEquals(PipelineCompletion.Current("history:6"), completed.result)
        assertEquals(listOf("load", "validate:loaded", "execute:6"), calls)
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
        assertNotSame(contexts[0], contexts[1])
        assertNotSame(contexts[1], contexts[2])
    }

    @Test
    fun `a stage without next completes normally and later stages do not run`() {
        val probe = ConnectionProbe()
        var laterStageCalls = 0
        val pipeline = ConnectionTypedOperationPipeline.start<String> { "stopped early" }
            .then<Int> { _, _ ->
                laterStageCalls++
                1
            }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        assertEquals(
            PipelineCompletion.Earlier("stopped early"),
            assertIs<ExecuteResult.Success<PipelineCompletion<String, Int>>>(result).result,
        )
        assertEquals(0, laterStageCalls)
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
    }

    @Test
    fun `the last stage can finish without next and exposes its typed result`() {
        val result = ConnectionTypedOperationPipeline.start<String> { "finished" }
            .execute(OperationExecutionExecutor { ConnectionProbe().connection() })

        assertEquals("finished", assertIs<ExecuteResult.Success<String>>(result).result)
    }

    @Test
    fun `NEW is rejected without opening another connection`() {
        val probe = ConnectionProbe()
        var finalStageCalls = 0
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            context.next("first").connection(ConnectionDecision.REUSE)
        }
            .then<String> { value, context ->
                context.next(value).connection(ConnectionDecision.NEW)
            }.then<String> { value, _ ->
                finalStageCalls++
                value
            }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertIs<IllegalStateException>(failure.exception)
        assertTrue(failure.exception.message.orEmpty().contains("NEW"))
        assertEquals(0, finalStageCalls)
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
    }

    @Test
    fun `multiple next attempts remain a protocol failure even when caught`() {
        val probe = ConnectionProbe()
        var laterStageCalls = 0
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            context.next("first").connection(ConnectionDecision.REUSE)
            assertFailsWith<IllegalStateException> {
                context.next("second")
            }
            "ignored"
        }.then<String> { value, _ ->
            laterStageCalls++
            value
        }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, laterStageCalls)
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
    }

    @Test
    fun `next on the final stage is rejected before any second connection attempt`() {
        val probe = ConnectionProbe()
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            context.next("no next stage").connection(ConnectionDecision.REUSE)
        }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("final Pipeline Stage"))
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
    }

    @Test
    fun `connection next without a decision fails instead of silently continuing`() {
        val probe = ConnectionProbe()
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            context.next("unresolved")
            "returned"
        }.then<String> { value, _ -> value }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("must be completed"))
        assertEquals(1, probe.openCount.get())
    }

    @Test
    fun `transaction stages use one transaction and context has no connection method`() {
        val probe = ConnectionProbe()
        val contexts = mutableListOf<Any>()
        val pipeline = TransactionTypedOperationPipeline.start<String> { context ->
            contexts += context
            context.next("transaction-start")
        }.then<Int> { value, context ->
            contexts += context
            context.next(value.length)
        }.then<String> { length, context ->
            contexts += context
            "committed:$length"
        }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        val completed = assertIs<ExecuteResult.Success<PipelineCompletion<PipelineCompletion<String, Int>, String>>>(result)
        assertEquals(PipelineCompletion.Current("committed:17"), completed.result)
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
        assertEquals(1, probe.commitCount.get())
        assertEquals(0, probe.rollbackCount.get())
        assertEquals(1, probe.autoCommitChangeCount.get())
        assertNotSame(contexts[0], contexts[1])
        assertFalse(TransactionPipelineStageContext::class.java.methods.any { it.name == "connection" })
    }

    @Test
    fun `domain failure is an ordinary value and operation failure remains latched`() {
        data class DomainFailure(val reason: String)
        val domainFailure = DomainFailure("validation")
        val probe = ConnectionProbe()
        var nextSawDomainFailure = false
        val domainPipeline = ConnectionTypedOperationPipeline.start<DomainFailure> { context ->
            context.next(domainFailure).connection(ConnectionDecision.REUSE)
        }.then<String> { value, context ->
            nextSawDomainFailure = value === domainFailure
            context.next("still an ordinary payload").connection(ConnectionDecision.REUSE)
        }.then<Int> { payload, _ ->
            payload.length
        }

        val domainResult = domainPipeline.execute(OperationExecutionExecutor { probe.connection() })
        assertEquals(
            PipelineCompletion.Current(25),
            assertIs<ExecuteResult.Success<PipelineCompletion<PipelineCompletion<DomainFailure, String>, Int>>>(domainResult).result,
        )
        assertTrue(nextSawDomainFailure)

        val operationFailure = IllegalStateException("latched")
        val failedPipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            context.failed<String>(operationFailure)
            "returned after latching"
        }
        val operationResult = failedPipeline.execute(OperationExecutionExecutor { ConnectionProbe().connection() })
        assertSame(operationFailure, assertIs<ExecuteResult.Failure>(operationResult).exception)
    }

    @Test
    fun `context expires after its stage and a builder reexecution uses fresh contexts`() {
        val probe = ConnectionProbe()
        val contexts = mutableListOf<ConnectionPipelineStageContext<String>>()
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            contexts += context
            "ok"
        }
        val executor = OperationExecutionExecutor { probe.connection() }

        assertIs<ExecuteResult.Success<String>>(pipeline.execute(executor))
        assertFailsWith<IllegalStateException> { contexts.single().databasePlatformConfig }
        assertIs<ExecuteResult.Success<String>>(pipeline.execute(executor))

        assertEquals(2, probe.openCount.get())
        assertEquals(2, probe.closeCount.get())
        assertNotSame(contexts[0], contexts[1])
    }

    @Test
    fun `cross thread context access is rejected and remembered by the stage`() {
        val probe = ConnectionProbe()
        var crossThreadRejected = false
        val pipeline = ConnectionTypedOperationPipeline.start<String> { context ->
            val worker = thread {
                crossThreadRejected = runCatching { context.databasePlatformConfig }.isFailure
            }
            worker.join()
            "value"
        }

        val result = pipeline.execute(OperationExecutionExecutor { probe.connection() })

        assertTrue(crossThreadRejected)
        assertIs<ExecuteResult.Failure>(result)
        assertEquals(1, probe.openCount.get())
        assertEquals(1, probe.closeCount.get())
    }

    @Test
    fun `fatal Error identity survives cleanup failure`() {
        val fatal = AssertionError("fatal pipeline stage")
        val closeFailure = SQLException("connection close failed")
        val probe = ConnectionProbe(closeFailure)
        val pipeline = TransactionTypedOperationPipeline.start<String> { throw fatal }

        val thrown = assertFailsWith<AssertionError> {
            pipeline.execute(OperationExecutionExecutor { probe.connection() })
        }

        assertSame(fatal, thrown)
        assertEquals(listOf(closeFailure), thrown.suppressed.toList())
        assertEquals(1, probe.rollbackCount.get())
        assertEquals(1, probe.closeCount.get())
    }

    private class ConnectionProbe(
        private val closeFailure: SQLException? = null,
    ) {
        val openCount = AtomicInteger()
        val closeCount = AtomicInteger()
        val autoCommitChangeCount = AtomicInteger()
        val commitCount = AtomicInteger()
        val rollbackCount = AtomicInteger()

        fun connection(): Connection {
            openCount.incrementAndGet()
            return Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "close" -> {
                        closeCount.incrementAndGet()
                        closeFailure?.let { throw it }
                        null
                    }

                    "getAutoCommit" -> true
                    "setAutoCommit" -> { autoCommitChangeCount.incrementAndGet(); null }
                    "commit" -> { commitCount.incrementAndGet(); null }
                    "rollback" -> { rollbackCount.incrementAndGet(); null }
                    "isClosed" -> false
                    "toString" -> "ContinuationPipelineConnection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        else -> null
                    }
                }
            } as Connection
        }
    }
}
