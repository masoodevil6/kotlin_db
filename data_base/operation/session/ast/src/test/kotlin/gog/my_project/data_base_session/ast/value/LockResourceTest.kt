package gog.my_project.data_base_session.ast.value

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LockResourceTest {
    @Test
    fun `accepts non-empty resource values and preserves their contents`() {
        val padded = LockResource("  App  ", "  Lock  ")
        val cased = LockResource("App", "Lock")
        val unicode = LockResource("é", "資料")

        assertEquals("  App  ", padded.namespace)
        assertEquals("  Lock  ", padded.name)
        assertEquals("App", cased.namespace)
        assertEquals("Lock", cased.name)
        assertEquals("é", unicode.namespace)
        assertEquals("資料", unicode.name)
    }

    @Test
    fun `rejects empty namespace`() {
        assertFailsWith<IllegalArgumentException> { LockResource("", "lock") }
    }

    @Test
    fun `rejects empty name`() {
        assertFailsWith<IllegalArgumentException> { LockResource("namespace", "") }
    }
}
