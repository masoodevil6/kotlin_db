package gog.my_project.data_base_session.ast.value

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SessionAdvisoryLockNameGeneratorTest {
    @Test
    fun `matches the established golden vector`() {
        val resource = LockResource(namespace = "migration", name = "database-main")

        assertEquals(
            "kdb1_22f00cc29957a0c2cfb8e5b0106ce5b4dd546ef0173cc52970e19a5fa6",
            SessionAdvisoryLockNameGenerator.generate(resource),
        )
    }

    @Test
    fun `generates a deterministic 63 character name`() {
        val resource = LockResource("application", "migration")

        val first = SessionAdvisoryLockNameGenerator.generate(resource)
        val second = SessionAdvisoryLockNameGenerator.generate(resource)

        assertEquals(first, second)
        assertEquals(63, first.length)
        assertTrue(first.startsWith("kdb1_"))
    }

    @Test
    fun `different resources produce different names`() {
        val first = SessionAdvisoryLockNameGenerator.generate(LockResource("application", "migration"))
        val changedNamespace = SessionAdvisoryLockNameGenerator.generate(LockResource("application-v2", "migration"))
        val changedName = SessionAdvisoryLockNameGenerator.generate(LockResource("application", "migration-v2"))

        assertNotEquals(first, changedNamespace)
        assertNotEquals(first, changedName)
    }

    @Test
    fun `uses UTF-8 bytes and preserves distinct Unicode strings`() {
        val composed = SessionAdvisoryLockNameGenerator.generate(LockResource("é", "資料"))
        val decomposed = SessionAdvisoryLockNameGenerator.generate(LockResource("e\u0301", "資料"))

        assertNotEquals(composed, decomposed)
        assertEquals(63, composed.length)
        assertEquals(63, decomposed.length)
    }
}
