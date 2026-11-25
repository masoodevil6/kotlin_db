package gog.my_project.data_base_operation.executor.session

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_session.api.result.AcquireSessionLockResult
import gog.my_project.data_base_session.api.result.ReleaseSessionLockResult
import gog.my_project.data_base_operation.executor.session.result.SessionConsumer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class SessionConsumerTest {
    @Test
    fun `consumer creation is deferred and execute returns the exact acquire result`() {
        var actionCalls = 0
        val expected: ExecuteResult<AcquireSessionLockResult> =
            ExecuteResult.Success(AcquireSessionLockResult.Acquired)
        val consumer = SessionConsumer<AcquireSessionLockResult> {
            actionCalls += 1
            expected
        }

        assertEquals(0, actionCalls)

        val actual: ExecuteResult<AcquireSessionLockResult> = consumer.execute()

        assertSame(expected, actual)
        assertEquals(1, actionCalls)
    }

    @Test
    fun `consumer returns release result with its semantic type`() {
        val expected: ExecuteResult<ReleaseSessionLockResult> =
            ExecuteResult.Success(ReleaseSessionLockResult.NotOwned)
        val consumer = SessionConsumer<ReleaseSessionLockResult> { expected }

        val actual: ExecuteResult<ReleaseSessionLockResult> = consumer.execute()

        assertSame(expected, actual)
    }

    @Test
    fun `consumer propagates the original exception and error`() {
        val failure = IllegalStateException("deferred action failed")
        val consumer = SessionConsumer<AcquireSessionLockResult> { throw failure }

        assertSame(failure, assertFailsWith<IllegalStateException> { consumer.execute() })

        val fatal = AssertionError("deferred action failed fatally")
        val fatalConsumer = SessionConsumer<ReleaseSessionLockResult> { throw fatal }

        assertSame(fatal, assertFailsWith<AssertionError> { fatalConsumer.execute() })
    }
}
