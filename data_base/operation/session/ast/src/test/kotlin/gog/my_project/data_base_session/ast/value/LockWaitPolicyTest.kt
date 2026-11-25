package gog.my_project.data_base_session.ast.value

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class LockWaitPolicyTest {
    @Test
    fun `NoWait represents zero wait without a timeout duration`() {
        assertEquals(LockWaitPolicy.NoWait, LockWaitPolicy.NoWait)
    }

    @Test
    fun `accepts whole second timeout boundaries`() {
        assertEquals(1, LockWaitPolicy.Timeout.of(1.seconds).seconds)
        assertEquals(30, LockWaitPolicy.Timeout.of(30.seconds).seconds)
        assertEquals(300, LockWaitPolicy.Timeout.of(300.seconds).seconds)
    }

    @Test
    fun `rejects zero and negative durations`() {
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of(0.seconds) }
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of((-1).seconds) }
    }

    @Test
    fun `rejects fractional seconds`() {
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of(500_000_000.nanoseconds) }
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of(1_500_000_000.nanoseconds) }
    }

    @Test
    fun `rejects timeout greater than maximum`() {
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of(301.seconds) }
    }

    @Test
    fun `rejects positive and negative infinity`() {
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of(kotlin.time.Duration.INFINITE) }
        assertFailsWith<IllegalArgumentException> { LockWaitPolicy.Timeout.of(-kotlin.time.Duration.INFINITE) }
    }
}
