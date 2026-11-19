package gog.my_project.data_base_operation.executor.transaction

import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationFailureOwnerConcurrencyTest {
    @Test
    fun `concurrent ordinary failures retain one primary and every distinct failure once`() {
        val owner = OperationFailureOwner()
        val failures = List(12) { IllegalStateException("ordinary failure $it") }

        runConcurrently(failures.map { failure ->
            { owner.recordFailure(failure); Unit }
        })

        val primary = owner.firstFailure
        assertTrue(failures.any { it === primary })
        assertNull(owner.fatalError)
        failures.forEach { failure -> assertTrue(owner.hasRecordedFailure(failure)) }

        // laterFailures is private by design. A subsequent Fatal Error exposes the Owner's
        // accumulated ordinary failures through its established suppressed-exception contract.
        val fatalProbe = AssertionError("inspect retained ordinary failures")
        owner.recordFatal(fatalProbe)

        assertSame(primary, owner.firstFailure)
        val retained = fatalProbe.suppressed.toList()
        assertSame(primary, retained.firstOrNull())
        assertEquals(failures.size, retained.size)
        failures.forEach { failure ->
            assertEquals(1, retained.count { it === failure })
        }
    }

    @Test
    fun `concurrent ordinary failures and fatal Error retain all identities exactly once`() {
        val owner = OperationFailureOwner()
        val ordinaryFailures = List(10) { IllegalArgumentException("ordinary failure $it") }
        val fatal = AssertionError("specific concurrent fatal Error")
        val actions: List<() -> Unit> = ordinaryFailures.map { failure ->
            { owner.recordFailure(failure); Unit }
        } + listOf({ owner.recordFatal(fatal) })

        runConcurrently(actions)

        assertSame(fatal, owner.fatalError)
        assertSame(fatal, owner.propagationFailure)
        assertTrue(owner.firstFailure === fatal || ordinaryFailures.any { it === owner.firstFailure })
        assertTrue(owner.hasRecordedFailure(fatal))

        // Concurrent monitor acquisition order is intentionally unspecified. Check identity
        // membership and cardinality rather than imposing an order on suppressed failures.
        val retained = fatal.suppressed.toList()
        assertEquals(ordinaryFailures.size, retained.size)
        ordinaryFailures.forEach { failure ->
            assertEquals(1, retained.count { it === failure })
        }
    }

    private fun runConcurrently(actions: List<() -> Unit>) {
        require(actions.isNotEmpty())

        val barrier = CyclicBarrier(actions.size + 1)
        val completed = CountDownLatch(actions.size)
        val workerFailures = arrayOfNulls<Throwable>(actions.size)
        val workers = actions.mapIndexed { index, action ->
            thread(start = true, name = "operation-failure-owner-$index") {
                try {
                    barrier.await(30, TimeUnit.SECONDS)
                    action()
                } catch (failure: Throwable) {
                    workerFailures[index] = failure
                } finally {
                    completed.countDown()
                }
            }
        }

        var barrierFailure: Throwable? = null
        try {
            barrier.await(30, TimeUnit.SECONDS)
        } catch (failure: Throwable) {
            barrierFailure = failure
        }

        val allCompleted = completed.await(30, TimeUnit.SECONDS)
        workers.forEach { it.join(30_000) }
        assertTrue(allCompleted, "Not all failure-recording workers completed")
        assertNull(barrierFailure, "The start barrier did not release all participants")
        assertTrue(workers.none { it.isAlive }, "A failure-recording worker remained alive")
        assertTrue(
            workerFailures.all { it == null },
            "Worker failure(s): ${workerFailures.filterNotNull().joinToString()}",
        )
    }
}
