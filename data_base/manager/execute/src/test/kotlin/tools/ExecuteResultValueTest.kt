package gog.my_project.data_base_manager.execute.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExecuteResultValueTest {

    @Test
    fun `toValue returns the original successful payload`() {
        val payload = Any()

        assertSame(payload, ExecuteResult.Success(payload).toValue())
    }

    @Test
    fun `toValue rethrows the original failure`() {
        val original = IllegalArgumentException("query failed")

        val thrown = assertFailsWith<IllegalArgumentException> {
            ExecuteResult.Failure(original).toValue()
        }

        assertSame(original, thrown)
    }

    @Test
    fun `toValue rejects a successful null payload`() {
        val thrown = assertFailsWith<IllegalStateException> {
            ExecuteResult.Success<String>(null).toValue()
        }

        assertEquals("Successful result contains no value", thrown.message)
    }

    @Test
    fun `success transforms a non-null value and preserves its generic result type`() {
        var transformCalls = 0
        val original: ExecuteResult<Int> = ExecuteResult.Success(42)

        val transformed: ExecuteResult<String> = original.success { value ->
            transformCalls++
            "value:$value"
        }

        assertEquals("value:42", assertIs<ExecuteResult.Success<String>>(transformed).result)
        assertEquals(1, transformCalls)
    }

    @Test
    fun `success skips null values and preserves failure without invoking transform`() {
        var transformCalls = 0
        val nullSuccess: ExecuteResult<String> = ExecuteResult.Success<Int>(null).success {
            transformCalls++
            it.toString()
        }
        assertNull(assertIs<ExecuteResult.Success<String>>(nullSuccess).result)

        val failure = ExecuteResult.Failure(IllegalArgumentException("query failed"))
        val preserved: ExecuteResult<String> = failure.success {
            transformCalls++
            it.toString()
        }

        assertSame(failure, preserved)
        assertEquals(0, transformCalls)
    }

    @Test
    fun `success propagates the original transformation exception`() {
        val expected = IllegalStateException("transform failed")

        val actual = assertFailsWith<IllegalStateException> {
            ExecuteResult.Success("value").success<String> { throw expected }
        }

        assertSame(expected, actual)
    }

    @Test
    fun `error observes only failure and returns the same result`() {
        var observed: Throwable? = null
        var handlerCalls = 0
        val failureCause = IllegalArgumentException("query failed")
        val failure: ExecuteResult<String> = ExecuteResult.Failure(failureCause)

        val returnedFailure = failure.error {
            handlerCalls++
            observed = it
        }

        assertSame(failureCause, observed)
        assertSame(failure, returnedFailure)
        assertEquals(1, handlerCalls)

        val success: ExecuteResult<String> = ExecuteResult.Success("ok")
        val returnedSuccess = success.error { handlerCalls++ }

        assertSame(success, returnedSuccess)
        assertEquals(1, handlerCalls)
    }

    @Test
    fun `error propagates the original handler exception`() {
        val expected = IllegalStateException("handler failed")

        val actual = assertFailsWith<IllegalStateException> {
            ExecuteResult.Failure(RuntimeException("query failed")).error { throw expected }
        }

        assertSame(expected, actual)
    }
}
