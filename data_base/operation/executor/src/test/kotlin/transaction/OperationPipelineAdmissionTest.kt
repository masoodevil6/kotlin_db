package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base.dialect.DatabaseDialect
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OperationPipelineAdmissionTest {
    @Test
    fun `Failure recorded before callback admission prevents entry`() {
        val owner = OperationFailureOwner()
        val failure = IllegalStateException("pre-existing operation failure")

        assertSame(failure, owner.recordFailure(failure))
        assertFalse(owner.tryEnterCallback())
    }

    @Test
    fun `callback admitted first then retains concurrent rejection and guarded calls observe it`() {
        val owner = OperationFailureOwner()
        val failure = IllegalStateException("rejected after callback admission")
        val recorded = CountDownLatch(1)

        assertTrue(owner.tryEnterCallback())
        val worker = thread(start = true) {
            owner.recordFailure(failure)
            recorded.countDown()
        }

        assertTrue(recorded.await(5, TimeUnit.SECONDS))
        worker.join()
        assertSame(failure, owner.firstFailure)

        val scope = OperationExecutionScope(
            connection(mutableListOf()),
            DatabasePlatformConfig(DatabaseDialect.MYSQL),
            null,
            null,
            owner,
        )
        try {
            val result = scope.executeRead("SELECT 1", emptyList()) { Unit }
            assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        } finally {
            scope.endForLifecycle()
        }
    }

    @Test
    fun `Connection preparation does not invoke callback after rejection during acquisition`() {
        val coordinator = OperationExecutionCoordinator()
        val rejection = IllegalStateException("rejected while acquiring Connection")
        val events = mutableListOf<String>()
        var acquisitions = 0
        var callbackInvoked = false
        var admission: OperationExecutionCoordinator.OperationAdmission? = null
        val acquisitionRejected = CountDownLatch(1)
        val executor = OperationExecutionExecutor {
            acquisitions++
            val worker = thread(start = true) {
                try {
                    admission = coordinator.acquireRootOperation(rejection)
                } finally {
                    acquisitionRejected.countDown()
                }
            }
            assertTrue(acquisitionRejected.await(5, TimeUnit.SECONDS))
            worker.join()
            connection(events)
        }

        val result = executor.connectionWithPreparation<Unit, String>(
            coordinator,
            prepare = { Unit },
            block = { _, _ ->
                callbackInvoked = true
                ExecuteResult.Success("must not run")
            },
        )

        assertIs<OperationExecutionCoordinator.OperationAdmission.Rejected>(admission)
        assertFalse(callbackInvoked)
        assertSame(rejection, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, acquisitions)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `Transaction preparation does not invoke callback after rejection during acquisition`() {
        val coordinator = OperationExecutionCoordinator()
        val rejection = IllegalStateException("rejected while acquiring Transaction")
        val events = mutableListOf<String>()
        var acquisitions = 0
        var callbackInvoked = false
        var admission: OperationExecutionCoordinator.OperationAdmission? = null
        val acquisitionRejected = CountDownLatch(1)
        val executor = OperationExecutionExecutor {
            acquisitions++
            val worker = thread(start = true) {
                try {
                    admission = coordinator.acquireRootOperation(rejection)
                } finally {
                    acquisitionRejected.countDown()
                }
            }
            assertTrue(acquisitionRejected.await(5, TimeUnit.SECONDS))
            worker.join()
            connection(events)
        }

        val result = executor.transactionWithPreparation<Unit, String>(
            coordinator,
            prepare = { Unit },
            block = { _, _ ->
                callbackInvoked = true
                ExecuteResult.Success("must not run")
            },
        )

        assertIs<OperationExecutionCoordinator.OperationAdmission.Rejected>(admission)
        assertFalse(callbackInvoked)
        assertSame(rejection, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, acquisitions)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `preparation failure acquires no Connection and Coordinator is reusable`() {
        val coordinator = OperationExecutionCoordinator()
        val preparationFailure = IllegalArgumentException("invalid Pipeline definition")
        val events = mutableListOf<String>()
        var acquisitions = 0
        val executor = OperationExecutionExecutor {
            acquisitions++
            connection(events)
        }

        val failed = executor.connectionWithPreparation<Unit, String>(
            coordinator,
            prepare = { throw preparationFailure },
            block = { _, _ -> ExecuteResult.Success("unreachable") },
        )

        assertSame(preparationFailure, assertIs<ExecuteResult.Failure>(failed).exception)
        assertEquals(0, acquisitions)
        assertTrue(events.isEmpty())

        val next = executor.connection(coordinator) { ExecuteResult.Success("next operation") }
        assertEquals("next operation", assertIs<ExecuteResult.Success<String>>(next).result)
        assertEquals(1, acquisitions)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `fatal preparation Error keeps identity and does not acquire resources`() {
        val coordinator = OperationExecutionCoordinator()
        val fatal = AssertionError("fatal during preparation")
        var acquisitions = 0
        val executor = OperationExecutionExecutor {
            acquisitions++
            connection(mutableListOf())
        }

        var thrown: Throwable? = null
        try {
            executor.transactionWithPreparation<Unit, String>(
                coordinator,
                prepare = { throw fatal },
                block = { _, _ -> ExecuteResult.Success("unreachable") },
            )
        } catch (failure: Throwable) {
            thrown = failure
        }

        assertSame(fatal, thrown)
        assertEquals(0, acquisitions)
        val next = executor.transaction(coordinator) { ExecuteResult.Success("reused") }
        assertEquals("reused", assertIs<ExecuteResult.Success<String>>(next).result)
        assertEquals(1, acquisitions)
    }

    @Test
    fun `rejection during STARTING belongs to admitted Owner and later admission gets a fresh Owner`() {
        val coordinator = OperationExecutionCoordinator()
        var firstOwner: OperationFailureOwner? = null
        var rejectedStart: OperationExecutionCoordinator.PipelineScopeStart? = null
        var firstScopeStarted = false
        assertNull(OperationScopeGuard.enter())
        try {
            val first = assertIs<OperationExecutionCoordinator.PipelineScopeStart.Started>(
                coordinator.tryStartPipelineScope(IllegalStateException("unused")),
            ).failureOwner
            firstScopeStarted = true
            firstOwner = first
            OperationScopeGuard.installPipelineFailureOwner(first)

            val rejection = IllegalStateException("concurrent STARTING rejection")
            val completed = CountDownLatch(1)
            val worker = thread(start = true) {
                try {
                    rejectedStart = coordinator.tryStartPipelineScope(rejection)
                } finally {
                    completed.countDown()
                }
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            worker.join()
            val result = assertIs<OperationExecutionCoordinator.PipelineScopeStart.Rejected>(rejectedStart)
            assertSame(rejection, result.failure)
            assertSame(rejection, first.firstFailure)
            assertSame(rejection, coordinator.finishPipelinePreparation(first))
        } finally {
            if (firstScopeStarted) coordinator.finishOwnedScope()
            OperationScopeGuard.exit()
        }

        var secondScopeStarted = false
        assertNull(OperationScopeGuard.enter())
        try {
            val second = assertIs<OperationExecutionCoordinator.PipelineScopeStart.Started>(
                coordinator.tryStartPipelineScope(IllegalStateException("unused")),
            ).failureOwner
            secondScopeStarted = true
            assertNotSame(firstOwner, second)
            val failure = IllegalStateException("end second preparation")
            second.recordFailure(failure)
            assertSame(failure, coordinator.finishPipelinePreparation(second))
        } finally {
            if (secondScopeStarted) coordinator.finishOwnedScope()
            OperationScopeGuard.exit()
        }
    }

    @Test
    fun `rejection during ACQUIRING remains on the Owner transferred to activated Scope`() {
        val coordinator = OperationExecutionCoordinator()
        val events = mutableListOf<String>()
        assertNull(OperationScopeGuard.enter())
        var scope: OperationExecutionScope? = null
        var scopeStarted = false
        try {
            val owner = assertIs<OperationExecutionCoordinator.PipelineScopeStart.Started>(
                coordinator.tryStartPipelineScope(IllegalStateException("unused")),
            ).failureOwner
            scopeStarted = true
            OperationScopeGuard.installPipelineFailureOwner(owner)
            assertSame(
                OperationExecutionCoordinator.PipelineAcquisitionClaim.Claimed,
                coordinator.claimPipelineAcquisition(owner),
            )

            val rejection = IllegalStateException("concurrent ACQUIRING rejection")
            var admission: OperationExecutionCoordinator.OperationAdmission? = null
            val completed = CountDownLatch(1)
            val worker = thread(start = true) {
                try {
                    admission = coordinator.acquireRootOperation(rejection)
                } finally {
                    completed.countDown()
                }
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            worker.join()
            assertSame(rejection, assertIs<OperationExecutionCoordinator.OperationAdmission.Rejected>(admission).failure)
            assertSame(rejection, owner.firstFailure)

            val activeScope = OperationExecutionScope(
                connection(events),
                DatabasePlatformConfig(DatabaseDialect.MYSQL),
                null,
                null,
                owner,
            )
            scope = activeScope
            coordinator.prepareScope(activeScope)
            OperationScopeGuard.activate(activeScope)
            coordinator.activateOwned(activeScope)

            assertSame(owner, activeScope.operationFailureOwner)
            assertSame(rejection, activeScope.rejectOperation())
            coordinator.sealOwned(activeScope)
        } finally {
            scope?.endForLifecycle()
            if (scopeStarted) coordinator.finishOwnedScope()
            OperationScopeGuard.exit()
        }
    }

    @Test
    fun `Connection callback admitted before later rejection can continue but guarded work is blocked`() {
        val coordinator = OperationExecutionCoordinator()
        val rejection = IllegalStateException("rejected after Connection callback admission")
        val events = mutableListOf<String>()
        var callbackInvoked = false
        var nestedAdmission: OperationExecutionCoordinator.OperationAdmission? = null
        val rejectionRecorded = CountDownLatch(1)
        val executor = OperationExecutionExecutor { connection(events) }

        val result = executor.connectionWithPreparation<Unit, String>(
            coordinator,
            prepare = { Unit },
            block = { scope, _ ->
                callbackInvoked = true
                val owner = scope.operationFailureOwner
                val worker = thread(start = true) {
                    try {
                        nestedAdmission = coordinator.acquireRootOperation(rejection)
                    } finally {
                        rejectionRecorded.countDown()
                    }
                }
                assertTrue(rejectionRecorded.await(5, TimeUnit.SECONDS))
                worker.join()
                assertSame(rejection, owner.firstFailure)
                val guarded = scope.executeRead("SELECT 1", emptyList()) { Unit }
                assertSame(rejection, assertIs<ExecuteResult.Failure>(guarded).exception)
                ExecuteResult.Success("callback was admitted")
            },
        )

        assertIs<OperationExecutionCoordinator.OperationAdmission.Rejected>(nestedAdmission)
        assertTrue(callbackInvoked)
        assertSame(rejection, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("close"), events)
    }

    @Test
    fun `Transaction callback admitted before later rejection keeps latch and rolls back`() {
        val coordinator = OperationExecutionCoordinator()
        val rejection = IllegalStateException("rejected after Transaction callback admission")
        val events = mutableListOf<String>()
        var callbackInvoked = false
        var nestedAdmission: OperationExecutionCoordinator.OperationAdmission? = null
        val rejectionRecorded = CountDownLatch(1)
        val executor = OperationExecutionExecutor { connection(events) }

        val result = executor.transactionWithPreparation<Unit, String>(
            coordinator,
            prepare = { Unit },
            block = { scope, _ ->
                callbackInvoked = true
                val owner = scope.operationFailureOwner
                val worker = thread(start = true) {
                    try {
                        nestedAdmission = coordinator.acquireRootOperation(rejection)
                    } finally {
                        rejectionRecorded.countDown()
                    }
                }
                assertTrue(rejectionRecorded.await(5, TimeUnit.SECONDS))
                worker.join()
                assertSame(rejection, owner.firstFailure)
                val guarded = scope.executeRead("SELECT 1", emptyList()) { Unit }
                assertSame(rejection, assertIs<ExecuteResult.Failure>(guarded).exception)
                ExecuteResult.Success("callback was admitted")
            },
        )

        assertIs<OperationExecutionCoordinator.OperationAdmission.Rejected>(nestedAdmission)
        assertTrue(callbackInvoked)
        assertSame(rejection, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)
    }

    @Test
    fun `fatal Owner identity and suppression order are retained`() {
        val owner = OperationFailureOwner()
        val first = IllegalStateException("first")
        val later = IllegalArgumentException("later")
        val fatal = AssertionError("fatal")
        val laterFatal = LinkageError("later fatal")

        owner.recordFailure(first)
        owner.recordFailure(later)
        owner.recordFatal(fatal)
        owner.recordFatal(laterFatal)

        assertSame(fatal, owner.fatalError)
        assertSame(fatal, owner.propagationFailure)
        assertEquals(listOf(first, later, laterFatal), fatal.suppressed.toList())
    }

    @Test
    fun `prepared Connection callback failures clean Guard and Coordinator for same-thread reuse`() {
        val coordinator = OperationExecutionCoordinator()
        val events = mutableListOf<String>()
        var acquisitions = 0
        val executor = OperationExecutionExecutor {
            acquisitions++
            connection(events)
        }

        val callbackException = IllegalStateException("Connection callback failed")
        var exceptionCallbackEntered = false
        val exceptionResult = executor.connectionWithPreparation<Unit, String>(
            coordinator,
            prepare = { Unit },
            block = { _, _ ->
                exceptionCallbackEntered = true
                throw callbackException
            },
        )
        assertTrue(exceptionCallbackEntered)
        assertSame(callbackException, assertIs<ExecuteResult.Failure>(exceptionResult).exception)
        assertEquals(1, acquisitions)
        assertEquals(listOf("close"), events)

        val afterException = executor.connection(coordinator) { ExecuteResult.Success("after Exception") }
        assertEquals("after Exception", assertIs<ExecuteResult.Success<String>>(afterException).result)
        assertEquals(2, acquisitions)
        assertEquals(listOf("close", "close"), events)

        val callbackError = AssertionError("Connection callback Error")
        var errorCallbackEntered = false
        var observedError: Throwable? = null
        try {
            executor.connectionWithPreparation<Unit, String>(
                coordinator,
                prepare = { Unit },
                block = { _, _ ->
                    errorCallbackEntered = true
                    throw callbackError
                },
            )
        } catch (failure: Throwable) {
            observedError = failure
        }
        assertTrue(errorCallbackEntered)
        assertSame(callbackError, observedError)
        assertEquals(3, acquisitions)
        assertEquals(listOf("close", "close", "close"), events)

        val afterError = executor.connection(coordinator) { ExecuteResult.Success("after Error") }
        assertEquals("after Error", assertIs<ExecuteResult.Success<String>>(afterError).result)
        assertEquals(4, acquisitions)
        assertEquals(listOf("close", "close", "close", "close"), events)
    }

    @Test
    fun `prepared Transaction callback failures clean Guard and Coordinator for same-thread reuse`() {
        val coordinator = OperationExecutionCoordinator()
        val events = mutableListOf<String>()
        var acquisitions = 0
        val executor = OperationExecutionExecutor {
            acquisitions++
            connection(events)
        }

        val callbackException = IllegalStateException("Transaction callback failed")
        var exceptionCallbackEntered = false
        val exceptionResult = executor.transactionWithPreparation<Unit, String>(
            coordinator,
            prepare = { Unit },
            block = { _, _ ->
                exceptionCallbackEntered = true
                throw callbackException
            },
        )
        assertTrue(exceptionCallbackEntered)
        assertSame(callbackException, assertIs<ExecuteResult.Failure>(exceptionResult).exception)
        assertEquals(1, acquisitions)
        assertEquals(listOf("autoCommit:false", "rollback", "close"), events)

        val afterException = executor.transaction(coordinator) { ExecuteResult.Success("after Exception") }
        assertEquals("after Exception", assertIs<ExecuteResult.Success<String>>(afterException).result)
        assertEquals(2, acquisitions)
        assertEquals(
            listOf("autoCommit:false", "rollback", "close", "autoCommit:false", "commit", "close"),
            events,
        )

        val callbackError = AssertionError("Transaction callback Error")
        var errorCallbackEntered = false
        var observedError: Throwable? = null
        try {
            executor.transactionWithPreparation<Unit, String>(
                coordinator,
                prepare = { Unit },
                block = { _, _ ->
                    errorCallbackEntered = true
                    throw callbackError
                },
            )
        } catch (failure: Throwable) {
            observedError = failure
        }
        assertTrue(errorCallbackEntered)
        assertSame(callbackError, observedError)
        assertEquals(3, acquisitions)
        assertEquals(
            listOf(
                "autoCommit:false", "rollback", "close",
                "autoCommit:false", "commit", "close",
                "autoCommit:false", "rollback", "close",
            ),
            events,
        )

        val afterError = executor.transaction(coordinator) { ExecuteResult.Success("after Error") }
        assertEquals("after Error", assertIs<ExecuteResult.Success<String>>(afterError).result)
        assertEquals(4, acquisitions)
        assertEquals(
            listOf(
                "autoCommit:false", "rollback", "close",
                "autoCommit:false", "commit", "close",
                "autoCommit:false", "rollback", "close",
                "autoCommit:false", "commit", "close",
            ),
            events,
        )
    }

    @Test
    fun `legacy and Pipeline Connection and Transaction admission matrix preserves incumbent failure`() {
        for (transactional in listOf(false, true)) {
            for (pipelineOuter in listOf(false, true)) {
                for (pipelineNested in listOf(false, true)) {
                    val outcome = runCompatibilityCase(transactional, pipelineOuter, pipelineNested)
                    val nestedFailure = assertIs<ExecuteResult.Failure>(outcome.nested).exception
                    val outerFailure = assertIs<ExecuteResult.Failure>(outcome.outer).exception
                    assertSame(nestedFailure, outerFailure)
                    assertSame(nestedFailure, outcome.guardFailureAfterNestedRejection)
                    assertEquals(if (pipelineOuter) 0 else 1, outcome.acquisitions)
                }
            }
        }
    }

    private data class CompatibilityOutcome(
        val outer: ExecuteResult<String>,
        val nested: ExecuteResult<String>,
        val guardFailureAfterNestedRejection: Throwable?,
        val acquisitions: Int,
    )

    private fun runCompatibilityCase(
        transactional: Boolean,
        pipelineOuter: Boolean,
        pipelineNested: Boolean,
    ): CompatibilityOutcome {
        val coordinator = OperationExecutionCoordinator()
        val events = mutableListOf<String>()
        var acquisitions = 0
        var nested: ExecuteResult<String>? = null
        var guardFailureAfterNestedRejection: Throwable? = null
        val executor = OperationExecutionExecutor {
            acquisitions++
            connection(events)
        }

        fun invokeNested(): ExecuteResult<String> = when {
            pipelineNested && transactional -> executor.transactionWithPreparation(
                coordinator,
                prepare = { Unit },
                block = { _, _ -> ExecuteResult.Success("unreachable") },
            )
            pipelineNested -> executor.connectionWithPreparation(
                coordinator,
                prepare = { Unit },
                block = { _, _ -> ExecuteResult.Success("unreachable") },
            )
            transactional -> executor.transaction(coordinator) { ExecuteResult.Success("unreachable") }
            else -> executor.connection(coordinator) { ExecuteResult.Success("unreachable") }
        }

        fun verifyOuterGuardRemainsOwned() {
            val failure = OperationScopeGuard.enter()
            if (failure == null) OperationScopeGuard.exit()
            guardFailureAfterNestedRejection = failure
        }

        val outer = when {
            pipelineOuter && transactional -> executor.transactionWithPreparation(
                coordinator,
                prepare = { nested = invokeNested(); verifyOuterGuardRemainsOwned(); Unit },
                block = { _, _ -> ExecuteResult.Success("outer callback") },
            )
            pipelineOuter -> executor.connectionWithPreparation(
                coordinator,
                prepare = { nested = invokeNested(); verifyOuterGuardRemainsOwned(); Unit },
                block = { _, _ -> ExecuteResult.Success("outer callback") },
            )
            transactional -> executor.transaction(coordinator) {
                nested = invokeNested()
                verifyOuterGuardRemainsOwned()
                ExecuteResult.Success("outer callback")
            }
            else -> executor.connection(coordinator) {
                nested = invokeNested()
                verifyOuterGuardRemainsOwned()
                ExecuteResult.Success("outer callback")
            }
        }

        return CompatibilityOutcome(
            outer,
            requireNotNull(nested),
            guardFailureAfterNestedRejection,
            acquisitions,
        )
    }

    private fun connection(events: MutableList<String>): Connection = Proxy.newProxyInstance(
        Connection::class.java.classLoader,
        arrayOf(Connection::class.java),
        InvocationHandler { proxy, method, args ->
            when (method.name) {
                "close" -> { events += "close"; null }
                "setAutoCommit" -> { events += "autoCommit:${args?.get(0)}"; null }
                "getAutoCommit" -> true
                "commit", "rollback" -> { events += method.name; null }
                "isClosed" -> events.contains("close")
                "toString" -> "AdmissionTestConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> null
            }
        },
    ) as Connection
}
