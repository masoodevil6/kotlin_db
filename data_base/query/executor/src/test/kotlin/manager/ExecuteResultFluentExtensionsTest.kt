package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.then
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame

class ExecuteResultFluentExtensionsTest {

    @Test
    fun `success transforms a successful value and error remains an observer`() {
        var successCalls = 0
        var errorCalls = 0

        val result: ExecuteResult<String> = ExecuteResult.Success(42)
            .success { value ->
                successCalls++
                "value:$value"
            }
            .error { errorCalls++ }

        assertEquals("value:42", assertIs<ExecuteResult.Success<String>>(result).result)
        assertEquals(1, successCalls)
        assertEquals(0, errorCalls)
    }

    @Test
    fun `then flattens an intentional failure and keeps the chain observable`() {
        val expected = IllegalStateException("business rule rejected result")
        var successCalls = 0
        var thenCalls = 0
        var errorCalls = 0

        val result: ExecuteResult<Int> = ExecuteResult.Success(17)
            .success { value ->
                successCalls++
                "id:$value"
            }
            .then { mapped ->
                thenCalls++
                assertEquals("id:17", mapped)
                ExecuteResult.Failure(expected)
            }
            .error { failure ->
                errorCalls++
                assertSame(expected, failure)
            }

        assertSame(expected, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, successCalls)
        assertEquals(1, thenCalls)
        assertEquals(1, errorCalls)
    }

    @Test
    fun `then preserves an incoming failure without invoking its transform`() {
        val original = IllegalArgumentException("query failed")
        val initial: ExecuteResult<Int> = ExecuteResult.Failure(original)
        var thenCalls = 0
        var observed: Throwable? = null

        val result = initial
            .then { value ->
                thenCalls++
                ExecuteResult.Success(value)
            }
            .error { observed = it }

        assertSame(initial, result)
        assertSame(original, observed)
        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, thenCalls)
    }

    @Test
    fun `then receives successful null so it can deliberately fail the chain`() {
        val expected = IllegalStateException("missing generated value")
        var observedValue: String? = "not called"

        val result: ExecuteResult<String> = ExecuteResult.Success<String>(null)
            .then { value ->
                observedValue = value
                ExecuteResult.Failure(expected)
            }

        assertNull(observedValue)
        assertSame(expected, assertIs<ExecuteResult.Failure>(result).exception)
    }
}
