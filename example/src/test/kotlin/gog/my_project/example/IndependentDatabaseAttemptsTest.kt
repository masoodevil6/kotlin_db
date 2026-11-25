package gog.my_project.example

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class IndependentDatabaseAttemptsTest {
    @Test
    fun `both databases succeed independently`() {
        val attempts = mutableListOf<ExampleDatabaseAttempt>()
        var mainRan = false
        var analyticsRan = false

        runIndependentDatabaseAttempts(
            onAttempt = attempts::add,
            main = { mainRan = true },
            analytics = { analyticsRan = true },
        )

        assertTrue(mainRan)
        assertTrue(analyticsRan)
        assertEquals(listOf("MAIN", "ANALYTICS"), attempts.map { it.database })
        assertTrue(attempts.all { it.failure == null })
        assertNull(combinedDatabaseFailure(attempts))
    }

    @Test
    fun `main exception is retained and analytics still runs successfully`() {
        val attempts = mutableListOf<ExampleDatabaseAttempt>()
        val mainFailure = IllegalStateException("MAIN migration failed")
        var analyticsRan = false

        runIndependentDatabaseAttempts(
            onAttempt = attempts::add,
            main = { throw mainFailure },
            analytics = { analyticsRan = true },
        )

        assertTrue(analyticsRan)
        assertSame(mainFailure, attempts.first { it.database == "MAIN" }.failure)
        assertNull(attempts.first { it.database == "ANALYTICS" }.failure)
        val combined = requireNotNull(combinedDatabaseFailure(attempts))
        assertTrue(combined is RuntimeException)
        assertSame(mainFailure, combined.cause)
    }

    @Test
    fun `analytics exception does not change main success result`() {
        val attempts = mutableListOf<ExampleDatabaseAttempt>()
        val analyticsFailure = IllegalArgumentException("ANALYTICS migration failed")

        runIndependentDatabaseAttempts(
            onAttempt = attempts::add,
            main = {},
            analytics = { throw analyticsFailure },
        )

        assertNull(attempts.first { it.database == "MAIN" }.failure)
        assertSame(analyticsFailure, attempts.first { it.database == "ANALYTICS" }.failure)
        val combined = requireNotNull(combinedDatabaseFailure(attempts))
        assertTrue(combined is RuntimeException)
        assertSame(analyticsFailure, combined.cause)
    }

    @Test
    fun `both failures remain associated with their database`() {
        val attempts = mutableListOf<ExampleDatabaseAttempt>()
        val mainFailure = IllegalStateException("MAIN failed")
        val analyticsFailure = IllegalStateException("ANALYTICS failed")

        runIndependentDatabaseAttempts(
            onAttempt = attempts::add,
            main = { throw mainFailure },
            analytics = { throw analyticsFailure },
        )

        assertSame(mainFailure, attempts.first { it.database == "MAIN" }.failure)
        assertSame(analyticsFailure, attempts.first { it.database == "ANALYTICS" }.failure)
        val combined = requireNotNull(combinedDatabaseFailure(attempts))
        assertTrue(combined is RuntimeException)
        assertSame(mainFailure, combined.cause)
        assertEquals(1, combined.suppressed.size)
        val analyticsReported = combined.suppressed.single()
        assertTrue(analyticsReported.message!!.contains("ANALYTICS"))
        assertSame(analyticsFailure, analyticsReported.cause)
    }

    @Test
    fun `fatal error is preserved and stops later database execution`() {
        val attempts = mutableListOf<ExampleDatabaseAttempt>()
        val fatal = AssertionError("fatal JVM error")
        var analyticsRan = false

        val thrown = assertFailsWith<AssertionError> {
            runIndependentDatabaseAttempts(
                onAttempt = attempts::add,
                main = { throw fatal },
                analytics = { analyticsRan = true },
            )
        }

        assertSame(fatal, thrown)
        assertSame(fatal, attempts.single().failure)
        assertFalse(analyticsRan)
        assertSame(fatal, combinedDatabaseFailure(attempts))
    }
}
