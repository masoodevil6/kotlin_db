package gog.my_project.data_base.manager.execute.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
}
