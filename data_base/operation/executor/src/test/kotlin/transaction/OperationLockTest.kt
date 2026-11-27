package gog.my_project.data_base_operation.executor.transaction

import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnectionInvalidator
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base.core.events.DatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType
import gog.my_project.data_base_operation.executor.events.OperationEventListener
import gog.my_project.data_base_operation.executor.events.OperationLockEvent
import gog.my_project.data_base_operation.executor.events.OperationLockEventType
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_operation.then.OperationPipelineStageContext
import gog.my_project.data_base_session.ast.value.LockResource
import gog.my_project.data_base_session.ast.value.LockWaitPolicy
import gog.my_project.data_base_session.ast.value.SessionAdvisoryLockNameGenerator
import gog.my_project.data_base_session.builder.advisory_lock.acquireSessionAdvisoryLock
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Statement
import java.util.concurrent.TimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.ArrayDeque
import java.util.IdentityHashMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.concurrent.thread

class OperationLockTest {
    private val resource = LockResource("application", "cache-refresh")

    @Test
    fun `Migration capability uses fixed Operation resource and preserves nullable success`() {
        for (timeout in listOf(0, 60)) {
            val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
            val context = databaseContext(PoolProvider(listOf(jdbc)))
            val expectedName = SessionAdvisoryLockNameGenerator.generate(
                OperationLockResourceGenerator.generate(
                    requireNotNull(context.databaseIdentity), LockResource("textqb:migration", "system"),
                ),
            )
            val result = OperationExecutionExecutor(context).connection { scope ->
                scope.withMigrationFailureStatusCapability<String?>(timeout) { ExecuteResult.Success(null) }
            }
            assertEquals(null, assertIs<ExecuteResult.Success<String?>>(result).result)
            assertEquals<List<Any?>>(listOf(expectedName, timeout, expectedName), jdbc.boundValues)
        }
    }

    @Test
    fun `Migration capability converts timeout and invalid waits to failures without callback`() {
        for (timeout in listOf(0, -1, 301)) {
            val jdbc = FakeJdbc(if (timeout == 0) listOf(SqlScript(status = 0)) else emptyList())
            var invoked = false
            val result = OperationExecutionExecutor(databaseContext(PoolProvider(listOf(jdbc)))).connection { scope ->
                scope.withMigrationFailureStatusCapability(timeout) {
                    invoked = true
                    ExecuteResult.Success(Unit)
                }
            }
            val failure = assertIs<ExecuteResult.Failure>(result).exception
            if (timeout == 0) assertIs<TimeoutException>(failure) else assertIs<IllegalArgumentException>(failure)
            assertFalse(invoked)
            assertEquals(if (timeout == 0) 1 else 0, jdbc.prepareCount)
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun `acquire callback and release use one scope and the same owned connection`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))
        val context = databaseContext(provider)
        val executor = OperationExecutionExecutor(context)
        val expectedLockName = SessionAdvisoryLockNameGenerator.generate(
            OperationLockResourceGenerator.generate(requireNotNull(context.databaseIdentity), resource),
        )
        var callbackCount = 0

        val result = executor.connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) { callbackContext ->
                callbackCount++
                assertSame(scope, callbackContext)
                assertSame(jdbc.connection, scope.connection)
                ExecuteResult.Success("work")
            }
        }

        val lockResult = assertIs<OperationLockResult.Acquired<String>>(
            assertIs<ExecuteResult.Success<OperationLockResult<String>>>(result).result,
        )
        assertEquals("work", lockResult.value)
        assertEquals(1, callbackCount)
        assertEquals(2, jdbc.prepareCount)
        assertEquals(listOf("SELECT GET_LOCK(?, ?) AS acquired", "SELECT RELEASE_LOCK(?) AS released"), jdbc.sql)
        assertEquals(expectedLockName, jdbc.boundValues[0])
        assertEquals(jdbc.boundValues[0], jdbc.boundValues[2])
        assertTrue(provider.discarded.isEmpty())
        assertEquals(1, jdbc.connectionCloseCount)
        assertEquals(0, jdbc.abortCount)
        assertEquals(0, jdbc.commitCount)
        assertEquals(0, jdbc.rollbackCount)
        assertEquals(0, jdbc.autoCommitChangeCount)
    }

    @Test
    fun `semantic lock events correlate one attempt and precede Connection close`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))
        val context = databaseContext(provider)
        val events = mutableListOf<DatabaseEvent>()
        val listener = OperationEventListener.fromDatabaseEventListener { events += it }
        var acquiredEventObservedBeforeCallback = false

        val result = OperationExecutionExecutor(context, listener).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                acquiredEventObservedBeforeCallback = events.filterIsInstance<OperationLockEvent>().lastOrNull()?.type ==
                    OperationLockEventType.LOCK_ACQUIRED
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<ExecuteResult.Success<*>>(result)
        assertTrue(acquiredEventObservedBeforeCallback)
        val lockEvents = events.filterIsInstance<OperationLockEvent>()
        assertEquals(
            listOf(
                OperationLockEventType.LOCK_ACQUISITION_STARTED,
                OperationLockEventType.LOCK_ACQUIRED,
                OperationLockEventType.LOCK_RELEASE_STARTED,
                OperationLockEventType.LOCK_RELEASED,
            ),
            lockEvents.map { it.type },
        )
        assertEquals(1, lockEvents.map { it.lockAttemptId }.distinct().size)
        assertTrue(lockEvents.all { it.operationId != null })
        assertEquals(1, lockEvents.map { it.operationId }.distinct().size)
        assertTrue(lockEvents.all { it.namespace == resource.namespace && it.name == resource.name })
        assertTrue(lockEvents.none { it.cleanupAfterUnknownAcquisition })
        assertTrue(lockEvents.none { it.failure != null })
        assertTrue(lockEvents.none { it.toString().contains(requireNotNull(context.databaseIdentity).value) })
        assertEquals(resource.name, lockEvents.first().name)
        val lockReleasedIndex = events.indexOf(lockEvents.last())
        val connectionClosedIndex = events.indexOfFirst {
            it is OperationDatabaseEvent && it.type == OperationDatabaseEventType.CONNECTION_CLOSED
        }
        assertTrue(lockReleasedIndex >= 0 && lockReleasedIndex < connectionClosedIndex)
    }

    @Test
    fun `timeout emits only started and timed out semantic events`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 0)))
        val events = mutableListOf<DatabaseEvent>()
        val listener = OperationEventListener.fromDatabaseEventListener { events += it }
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(PoolProvider(listOf(jdbc))), listener)
            .connection { scope ->
                scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                    callbackInvoked = true
                    ExecuteResult.Success(Unit)
                }
            }

        assertIs<ExecuteResult.Success<*>>(result)
        assertFalse(callbackInvoked)
        val lockEvents = events.filterIsInstance<OperationLockEvent>()
        assertEquals(
            listOf(OperationLockEventType.LOCK_ACQUISITION_STARTED, OperationLockEventType.LOCK_TIMED_OUT),
            lockEvents.map { it.type },
        )
        assertEquals(1, lockEvents.map { it.lockAttemptId }.distinct().size)
        assertEquals(1, jdbc.prepareCount)
    }

    @Test
    fun `unknown acquisition cleanup emits unknown before a correlated best effort release`() {
        val jdbc = FakeJdbc(
            listOf(
                SqlScript(prepareFailure = SQLException("acquire response unavailable")),
                SqlScript(status = 0),
            ),
        )
        val events = mutableListOf<DatabaseEvent>()
        val listener = OperationEventListener.fromDatabaseEventListener { events += it }
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(PoolProvider(listOf(jdbc))), listener)
            .connection { scope ->
                scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                    callbackInvoked = true
                    ExecuteResult.Success(Unit)
                }
            }

        assertIs<ExecuteResult.Failure>(result)
        assertFalse(callbackInvoked)
        val lockEvents = events.filterIsInstance<OperationLockEvent>()
        assertEquals(
            listOf(
                OperationLockEventType.LOCK_ACQUISITION_STARTED,
                OperationLockEventType.LOCK_ACQUISITION_UNKNOWN,
                OperationLockEventType.LOCK_RELEASE_STARTED,
                OperationLockEventType.LOCK_NOT_OWNED,
            ),
            lockEvents.map { it.type },
        )
        assertEquals(1, lockEvents.map { it.lockAttemptId }.distinct().size)
        assertTrue(lockEvents.all { it.operationId != null })
        assertEquals(1, lockEvents.map { it.operationId }.distinct().size)
        assertTrue(lockEvents[1].failure != null)
        assertFalse(lockEvents[1].toString().contains("response unavailable"))
        assertTrue(lockEvents.drop(2).all { it.cleanupAfterUnknownAcquisition })
    }

    @Test
    fun `release failure is reported as unknown and callback failures still release`() {
        val releaseJdbc = FakeJdbc(
            listOf(SqlScript(status = 1), SqlScript(prepareFailure = SQLException("release response unavailable"))),
        )
        val releaseEvents = mutableListOf<DatabaseEvent>()
        val releaseListener = OperationEventListener.fromDatabaseEventListener { releaseEvents += it }
        val releaseResult = OperationExecutionExecutor(
            databaseContext(PoolProvider(listOf(releaseJdbc))),
            releaseListener,
        ).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
        }
        assertIs<ExecuteResult.Failure>(releaseResult)
        assertEquals(
            listOf(
                OperationLockEventType.LOCK_ACQUISITION_STARTED,
                OperationLockEventType.LOCK_ACQUIRED,
                OperationLockEventType.LOCK_RELEASE_STARTED,
                OperationLockEventType.LOCK_RELEASE_UNKNOWN,
            ),
            releaseEvents.filterIsInstance<OperationLockEvent>().map { it.type },
        )

        val callbackJdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val callbackEvents = mutableListOf<DatabaseEvent>()
        val callbackListener = OperationEventListener.fromDatabaseEventListener { callbackEvents += it }
        val callbackFailure = IllegalStateException("callback failed")
        val callbackResult = OperationExecutionExecutor(
            databaseContext(PoolProvider(listOf(callbackJdbc))),
            callbackListener,
        ).connection { scope ->
            scope.withOperationLock<Unit>(resource, LockWaitPolicy.NoWait) {
                ExecuteResult.Failure(callbackFailure)
            }
        }
        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(callbackResult).exception)
        assertEquals(
            OperationLockEventType.LOCK_RELEASED,
            callbackEvents.filterIsInstance<OperationLockEvent>().last().type,
        )
    }

    @Test
    fun `sequential lock brackets keep one Operation identity and distinct attempt identities`() {
        val jdbc = FakeJdbc(List(4) { SqlScript(status = 1) })
        val events = mutableListOf<DatabaseEvent>()
        val listener = OperationEventListener.fromDatabaseEventListener { events += it }

        val result = OperationExecutionExecutor(
            databaseContext(PoolProvider(listOf(jdbc))),
            listener,
        ).connection { scope ->
            val first = scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
            val second = scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
            assertIs<OperationLockResult.Acquired<Unit>>(
                assertIs<ExecuteResult.Success<OperationLockResult<Unit>>>(first).result,
            )
            assertIs<OperationLockResult.Acquired<Unit>>(
                assertIs<ExecuteResult.Success<OperationLockResult<Unit>>>(second).result,
            )
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        val lockEvents = events.filterIsInstance<OperationLockEvent>()
        val oneAttemptTypes = listOf(
            OperationLockEventType.LOCK_ACQUISITION_STARTED,
            OperationLockEventType.LOCK_ACQUIRED,
            OperationLockEventType.LOCK_RELEASE_STARTED,
            OperationLockEventType.LOCK_RELEASED,
        )
        assertEquals(oneAttemptTypes + oneAttemptTypes, lockEvents.map { it.type })
        assertEquals(2, lockEvents.chunked(4).map { it.first().lockAttemptId }.distinct().size)
        assertEquals(1, lockEvents.map { it.operationId }.distinct().size)
        assertTrue(lockEvents.all { it.operationId != null })
    }

    @Test
    fun `concurrent Root Operations retain independent lock attempt and operation identities`() {
        val entered = CountDownLatch(2)
        val release = CountDownLatch(1)
        val events = CopyOnWriteArrayList<DatabaseEvent>()
        val listener = OperationEventListener.fromDatabaseEventListener { events += it }
        val firstJdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val secondJdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val results = CopyOnWriteArrayList<ExecuteResult<OperationLockResult<Unit>>>()

        fun worker(jdbc: FakeJdbc) = thread {
            val executor = OperationExecutionExecutor(databaseContext(PoolProvider(listOf(jdbc))), listener)
            results += executor.connection { scope ->
                scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                    entered.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "Concurrent lock callbacks were not released" }
                    ExecuteResult.Success(Unit)
                }
            }
        }

        val first = worker(firstJdbc)
        val second = worker(secondJdbc)
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Both independent callbacks should be active")
        release.countDown()
        first.join(5_000)
        second.join(5_000)
        assertFalse(first.isAlive)
        assertFalse(second.isAlive)
        assertEquals(2, results.size)
        assertTrue(results.all { it is ExecuteResult.Success })

        val lockEvents = events.filterIsInstance<OperationLockEvent>()
        assertEquals(2, lockEvents.map { it.lockAttemptId }.distinct().size)
        assertEquals(2, lockEvents.map { it.operationId }.distinct().size)
        assertTrue(lockEvents.groupBy { it.lockAttemptId }.values.all { attempt ->
            attempt.map { it.operationId }.distinct().size == 1 && attempt.size == 4
        })
    }

    @Test
    fun `exception and Error callbacks each emit release before Connection close`() {
        val exceptionJdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val exceptionEvents = mutableListOf<DatabaseEvent>()
        val exceptionListener = OperationEventListener.fromDatabaseEventListener { exceptionEvents += it }
        val callbackException = IllegalStateException("callback exception")
        val exceptionResult = OperationExecutionExecutor(
            databaseContext(PoolProvider(listOf(exceptionJdbc))),
            exceptionListener,
        ).connection { scope ->
            scope.withOperationLock<Unit>(resource, LockWaitPolicy.NoWait) { throw callbackException }
        }
        assertSame(callbackException, assertIs<ExecuteResult.Failure>(exceptionResult).exception)
        assertEquals(
            OperationLockEventType.LOCK_RELEASED,
            exceptionEvents.filterIsInstance<OperationLockEvent>().last().type,
        )

        val errorJdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val errorEvents = mutableListOf<DatabaseEvent>()
        val errorListener = OperationEventListener.fromDatabaseEventListener { errorEvents += it }
        val callbackError = AssertionError("callback Error")
        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor(
                databaseContext(PoolProvider(listOf(errorJdbc))),
                errorListener,
            ).connection { scope ->
                scope.withOperationLock<Unit>(resource, LockWaitPolicy.NoWait) { throw callbackError }
            }
        }
        assertSame(callbackError, thrown)
        assertEquals(
            OperationLockEventType.LOCK_RELEASED,
            errorEvents.filterIsInstance<OperationLockEvent>().last().type,
        )
        val releaseIndex = errorEvents.indexOf(errorEvents.filterIsInstance<OperationLockEvent>().last())
        val closeIndex = errorEvents.indexOfFirst {
            it is OperationDatabaseEvent && it.type == OperationDatabaseEventType.CONNECTION_CLOSED
        }
        assertTrue(releaseIndex >= 0 && releaseIndex < closeIndex)
    }

    @Test
    fun `release NotOwned remains distinct from successful release`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 0)))
        val events = mutableListOf<DatabaseEvent>()
        val listener = OperationEventListener.fromDatabaseEventListener { events += it }

        val result = OperationExecutionExecutor(databaseContext(PoolProvider(listOf(jdbc))), listener)
            .connection { scope ->
                scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
            }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(
            OperationLockEventType.LOCK_NOT_OWNED,
            events.filterIsInstance<OperationLockEvent>().last().type,
        )
    }

    @Test
    fun `throwing Watch listener cannot change Operation lock result`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val listener = OperationEventListener.fromDatabaseEventListener { throw AssertionError("observer failed") }

        val result = OperationExecutionExecutor(databaseContext(PoolProvider(listOf(jdbc))), listener)
            .connection { scope ->
                scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success("done") }
            }

        val acquired = assertIs<OperationLockResult.Acquired<String>>(
            assertIs<ExecuteResult.Success<OperationLockResult<String>>>(result).result,
        )
        assertEquals("done", acquired.value)
        assertEquals(1, jdbc.connectionCloseCount)
    }

    @Test
    fun `timeout returns semantic result without invoking callback or release`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 0)))
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            val lockResult = scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
            assertEquals(OperationLockResult.TimedOut, assertIs<ExecuteResult.Success<*>>(lockResult).result)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(result)
        assertFalse(callbackInvoked)
        assertEquals(1, jdbc.prepareCount)
        assertTrue(provider.discarded.isEmpty())
    }

    @Test
    fun `effective lock resource is deterministic database scoped and produces a bounded Session name`() {
        val identityA = configuredIdentity("db-a.internal", "catalog_a")
        val identityB = configuredIdentity("db-a.internal", "catalog_b")
        val first = OperationLockResourceGenerator.generate(identityA, resource)
        val repeated = OperationLockResourceGenerator.generate(identityA, resource)
        val otherDatabase = OperationLockResourceGenerator.generate(identityB, resource)
        val firstSessionName = SessionAdvisoryLockNameGenerator.generate(first)
        val otherDatabaseSessionName = SessionAdvisoryLockNameGenerator.generate(otherDatabase)

        assertEquals(first, repeated)
        assertNotEquals(first, otherDatabase)
        assertNotEquals(firstSessionName, otherDatabaseSessionName)
        assertEquals(63, firstSessionName.length)
        assertTrue(firstSessionName.startsWith("kdb1_"))
        assertTrue(firstSessionName.all { it.code in 0x20..0x7e })
        assertTrue(first.namespace.all { it.code in 0x20..0x7e })
        assertTrue(first.name.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(
            OperationLockResourceGenerator.generate(identityA, LockResource("ab", "c")),
            OperationLockResourceGenerator.generate(identityA, LockResource("a", "bc")),
        )
    }

    @Test
    fun `missing DatabaseIdentity rejects Operation lock before preparing a statement`() {
        val jdbc = FakeJdbc(emptyList())
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider, includeIdentity = false)).connection { scope ->
            val lockResult = scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(lockResult)
            assertTrue(assertIs<ExecuteResult.Failure>(lockResult).exception.message.orEmpty().contains("DatabaseIdentity"))
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertFalse(callbackInvoked)
        assertEquals(0, jdbc.prepareCount)
    }

    @Test
    fun `nested Operation locks with same or different resources are rejected before nested JDBC`() {
        for (nestedResource in listOf(resource, LockResource("application", "other-work"))) {
            val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
            val provider = PoolProvider(listOf(jdbc))
            var nestedCallbackInvoked = false

            val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
                scope.withOperationLock(resource, LockWaitPolicy.NoWait) { activeScope ->
                    val nested = activeScope.withOperationLock(nestedResource, LockWaitPolicy.NoWait) {
                        nestedCallbackInvoked = true
                        ExecuteResult.Success(Unit)
                    }
                    assertIs<ExecuteResult.Failure>(nested)
                    ExecuteResult.Success(Unit)
                }
            }

            assertIs<ExecuteResult.Failure>(result)
            assertFalse(nestedCallbackInvoked)
            assertEquals(2, jdbc.prepareCount)
        }
    }

    @Test
    fun `direct Session command inside Operation lock is rejected and outer lock is released`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))
        val api = acquireSessionAdvisoryLock {
            resource("application", "direct-session")
            noWait()
        }

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) { activeScope ->
                assertIs<ExecuteResult.Failure>(activeScope.session(api).execute())
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(2, jdbc.prepareCount)
        assertEquals(2, jdbc.statementCloseCount)
    }

    @Test
    fun `Legacy lock bracket inside Operation lock is rejected and outer lock is released`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) { activeScope ->
                assertIs<ExecuteResult.Failure>(
                    scope.withSessionAdvisoryLock("legacy:nested", 1) { ExecuteResult.Success(Unit) },
                )
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(2, jdbc.prepareCount)
        assertEquals(2, jdbc.statementCloseCount)
    }

    @Test
    fun `acquire JDBC failure does not invoke callback and attempts cleanup`() {
        val jdbc = FakeJdbc(
            listOf(
                SqlScript(prepareFailure = SQLException("acquire prepare failed")),
                SqlScript(status = 0),
            ),
        )
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            val lockResult = scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(lockResult)
            ExecuteResult.Success(Unit)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertFalse(callbackInvoked)
        assertTrue(failure.message.orEmpty().contains("JDBC execution failed"))
        assertEquals(2, jdbc.prepareCount)
        assertEquals(1, provider.discarded.size)
        assertSame(jdbc.connection, provider.discarded.single())
    }

    @Test
    fun `confirmed acquire followed by result set close failure skips callback and discards connection`() {
        val jdbc = FakeJdbc(
            listOf(
                SqlScript(status = 1, resultSetCloseFailure = SQLException("acquire rows close failed")),
                SqlScript(status = 1),
            ),
        )
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("JDBC execution failed"))
        assertFalse(callbackInvoked)
        assertEquals(2, jdbc.prepareCount)
        assertEquals(1, provider.discarded.size)
        assertEquals(2, jdbc.resultSetCloseCount)
    }

    @Test
    fun `statement close failure after acquire result is treated as unknown and cleaned up`() {
        val jdbc = FakeJdbc(
            listOf(
                SqlScript(status = 1, statementCloseFailure = SQLException("acquire statement close failed")),
                SqlScript(status = 1),
            ),
        )
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
        }

        assertIs<ExecuteResult.Failure>(result)
        assertFalse(callbackInvoked)
        assertEquals(2, jdbc.prepareCount)
        assertEquals(1, provider.discarded.size)
    }

    @Test
    fun `callback ExecuteResult Failure remains primary after successful release`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))
        val callbackFailure = IllegalStateException("callback failure")

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                ExecuteResult.Failure(callbackFailure)
            }
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(2, jdbc.prepareCount)
        assertTrue(provider.discarded.isEmpty())
    }

    @Test
    fun `callback exception becomes Failure and release still runs`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))
        val callbackFailure = IllegalArgumentException("callback threw")

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock<Unit>(resource, LockWaitPolicy.NoWait) {
                throw callbackFailure
            }
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(2, jdbc.prepareCount)
        assertTrue(provider.discarded.isEmpty())
    }

    @Test
    fun `callback Error is rethrown by identity after release`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 1)))
        val provider = PoolProvider(listOf(jdbc))
        val callbackError = AssertionError("callback error")

        val thrown = assertFailsWith<AssertionError> {
            OperationExecutionExecutor(databaseContext(provider)).connection<Unit> { scope ->
                scope.withOperationLock<Unit>(resource, LockWaitPolicy.NoWait) {
                    throw callbackError
                }
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(callbackError, thrown)
        assertEquals(2, jdbc.prepareCount)
        assertTrue(provider.discarded.isEmpty())
    }

    @Test
    fun `release NotOwned becomes Failure after callback`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 1), SqlScript(status = 0)))
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("no longer owned"))
        assertTrue(callbackInvoked)
        assertTrue(provider.discarded.isEmpty())
    }

    @Test
    fun `release JDBC failure is returned and connection is discarded`() {
        val jdbc = FakeJdbc(
            listOf(
                SqlScript(status = 1),
                SqlScript(executeFailure = SQLException("release execute failed")),
            ),
        )
        val provider = PoolProvider(listOf(jdbc))

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("JDBC execution failed"))
        assertEquals(1, provider.discarded.size)
        assertEquals(2, jdbc.prepareCount)
    }

    @Test
    fun `callback failure stays primary and release failure is suppressed`() {
        val jdbc = FakeJdbc(
            listOf(
                SqlScript(status = 1),
                SqlScript(executeFailure = SQLException("release execute failed")),
            ),
        )
        val provider = PoolProvider(listOf(jdbc))
        val callbackFailure = IllegalStateException("callback is primary")

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                ExecuteResult.Failure(callbackFailure)
            }
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, callbackFailure.suppressed.size)
        assertTrue(callbackFailure.suppressed.single().message.orEmpty().contains("JDBC execution failed"))
        assertEquals(1, provider.discarded.size)
    }

    @Test
    fun `unknown acquire result never runs callback and performs release then discard`() {
        val jdbc = FakeJdbc(listOf(SqlScript(status = 7), SqlScript(status = 0)))
        val provider = PoolProvider(listOf(jdbc))
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                callbackInvoked = true
                ExecuteResult.Success(Unit)
            }
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("unexpected integer value"))
        assertFalse(callbackInvoked)
        assertEquals(2, jdbc.prepareCount)
        assertEquals(1, provider.discarded.size)
    }

    @Test
    fun `pre failed Scope rejects lock before preparing a statement`() {
        val jdbc = FakeJdbc(emptyList())
        val provider = PoolProvider(listOf(jdbc))
        val priorFailure = IllegalStateException("prior failure")

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            scope.failed<Unit>(priorFailure)
            val lockResult = scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                ExecuteResult.Success(Unit)
            }
            assertSame(priorFailure, assertIs<ExecuteResult.Failure>(lockResult).exception)
            ExecuteResult.Success(Unit)
        }

        assertSame(priorFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, jdbc.prepareCount)
    }

    @Test
    fun `expired Operation Scope rejects lock before preparing a statement`() {
        val jdbc = FakeJdbc(emptyList())
        val provider = PoolProvider(listOf(jdbc))
        var capturedScope: OperationExecutionScope? = null

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            capturedScope = scope
            ExecuteResult.Success(Unit)
        }
        assertIs<ExecuteResult.Success<Unit>>(result)

        var callbackInvoked = false
        val lockResult = requireNotNull(capturedScope).withOperationLock(resource, LockWaitPolicy.NoWait) {
            callbackInvoked = true
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(lockResult)
        assertFalse(callbackInvoked)
        assertEquals(0, jdbc.prepareCount)
    }

    @Test
    fun `unmanaged Scope rejects operation lock before JDBC`() {
        val jdbc = FakeJdbc(emptyList())
        val scope = OperationExecutionScope(jdbc.connection, DatabasePlatformConfig(DatabaseDialect.MYSQL))
        var callbackInvoked = false

        val result = scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
            callbackInvoked = true
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertFalse(callbackInvoked)
        assertEquals(0, jdbc.prepareCount)
    }

    @Test
    fun `wrong thread is rejected before acquire`() {
        val jdbc = FakeJdbc(emptyList())
        val provider = PoolProvider(listOf(jdbc))
        var workerResult: ExecuteResult<OperationLockResult<Unit>>? = null
        var callbackInvoked = false

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { scope ->
            val worker = thread {
                workerResult = scope.withOperationLock(resource, LockWaitPolicy.NoWait) {
                    callbackInvoked = true
                    ExecuteResult.Success(Unit)
                }
            }
            worker.join()
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertIs<ExecuteResult.Failure>(workerResult)
        assertFalse(callbackInvoked)
        assertEquals(0, jdbc.prepareCount)
    }

    @Test
    fun `transaction child rejects operation lock before JDBC`() {
        val jdbc = FakeJdbc(emptyList())
        val provider = PoolProvider(listOf(jdbc))

        val result = OperationExecutionExecutor(databaseContext(provider)).connection { root ->
            val childResult = root.transaction { child ->
                val lockResult = child.withOperationLock(resource, LockWaitPolicy.NoWait) {
                    ExecuteResult.Success(Unit)
                }
                assertIs<ExecuteResult.Failure>(lockResult)
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(childResult)
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Failure>(result)
        assertEquals(0, jdbc.prepareCount)
    }

    @Test
    fun `expired Pipeline Stage rejects lock before delegate`() {
        val delegate = LockContextDelegate()
        val stage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = false)
        stage.expire()

        assertFailsWith<IllegalStateException> {
            stage.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
        }
        assertFalse(delegate.lockCalled)
    }

    @Test
    fun `Pipeline Stage remains the callback context for the full synchronous lock bracket`() {
        val delegate = LockContextDelegate()
        val stage = OperationPipelineStageContext<Unit>(delegate, Thread.currentThread(), hasNextStage = false)

        val result = stage.withOperationLock(resource, LockWaitPolicy.NoWait) { callbackContext ->
            assertSame(stage, callbackContext)
            ExecuteResult.Success("stage-work")
        }

        val acquired = assertIs<OperationLockResult.Acquired<String>>(
            assertIs<ExecuteResult.Success<OperationLockResult<String>>>(result).result,
        )
        assertEquals("stage-work", acquired.value)
        assertTrue(delegate.lockCalled)
        assertTrue(delegate.callbackInvoked)
    }

    @Test
    fun `provider discard prevents an unusable connection from returning to reuse`() {
        val first = FakeJdbc(
            listOf(
                SqlScript(status = 1),
                SqlScript(status = 1, resultSetCloseFailure = SQLException("release rows close failed")),
            ),
        )
        val second = FakeJdbc(emptyList())
        val provider = PoolProvider(listOf(first, second))
        val executor = OperationExecutionExecutor(databaseContext(provider))

        val firstResult = executor.connection { scope ->
            scope.withOperationLock(resource, LockWaitPolicy.NoWait) { ExecuteResult.Success(Unit) }
        }
        assertIs<ExecuteResult.Failure>(firstResult)
        assertSame(first.connection, provider.discarded.single())

        var secondConnection: Connection? = null
        val secondResult = executor.connection { scope ->
            @Suppress("DEPRECATION")
            secondConnection = scope.connection
            ExecuteResult.Success(Unit)
        }

        assertIs<ExecuteResult.Success<Unit>>(secondResult)
        assertSame(second.connection, secondConnection)
        assertFalse(first.connection === secondConnection)
    }

    @Test
    fun `default Provider discard calls JDBC abort`() {
        val jdbc = FakeJdbc(emptyList())
        val provider = object : IDatabaseConnection {
            override fun build(): Connection = jdbc.connection
        }

        databaseContext(provider).discardConnection(jdbc.connection)

        assertEquals(1, jdbc.abortCount)
        assertEquals(0, jdbc.connectionCloseCount)
    }

    private fun databaseContext(
        provider: IDatabaseConnection,
        includeIdentity: Boolean = true,
    ): DatabaseContext {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://localhost", 3306, "test", "user", ""),
            platform = DatabasePlatformConfig(DatabaseDialect.MYSQL),
        )
        return DatabaseContext(
            name = "operation-lock-test",
            config = config,
            connectionProvider = provider,
            databaseIdentity = if (includeIdentity) DatabaseIdentity.from(config) else null,
        )
    }

    private fun configuredIdentity(host: String, database: String): DatabaseIdentity {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig("jdbc:mysql://$host", 3306, database, "user", ""),
        )
        return requireNotNull(DatabaseIdentity.from(config))
    }

    private class LockContextDelegate : OperationExecutionContext {
        var lockCalled = false
        var callbackInvoked = false

        override fun executeDdl(sql: String, parameters: List<OperationParameter>): ExecuteResult<Boolean> =
            ExecuteResult.Success(true)

        override fun <T> withOperationLock(
            resource: LockResource,
            waitPolicy: LockWaitPolicy,
            block: (OperationExecutionContext) -> ExecuteResult<T>,
        ): ExecuteResult<OperationLockResult<T>> {
            lockCalled = true
            callbackInvoked = true
            return when (val callbackResult = block(this)) {
                is ExecuteResult.Failure -> callbackResult
                is ExecuteResult.Success -> ExecuteResult.Success(
                    OperationLockResult.Acquired(callbackResult.result),
                )
            }
        }

        override fun <T> failed(failure: Throwable): ExecuteResult<T> = ExecuteResult.Failure(failure)
    }

    private class PoolProvider(connections: List<FakeJdbc>) : IDatabaseConnection, IDatabaseConnectionInvalidator {
        private val allConnections = connections.toList()
        private val reusable = ArrayDeque<Connection>()
        private val discardedIdentities = IdentityHashMap<Connection, Boolean>()
        private var nextConnection = 0
        val discarded = mutableListOf<Connection>()

        init {
            allConnections.forEach { jdbc -> jdbc.onConnectionClose = ::returnToPool }
        }

        override fun build(): Connection {
            if (reusable.isNotEmpty()) return reusable.removeFirst()
            check(nextConnection < allConnections.size) { "No fake Connection remains" }
            return allConnections[nextConnection++].connection
        }

        override fun discard(connection: Connection) {
            discarded += connection
            discardedIdentities[connection] = true
            val kept = reusable.filterNot { it === connection }
            reusable.clear()
            reusable.addAll(kept)
        }

        private fun returnToPool(connection: Connection) {
            if (discardedIdentities[connection] != true) reusable.addLast(connection)
        }
    }

    private data class SqlScript(
        val status: Any? = 1,
        val executeReturnsResultSet: Boolean = true,
        val initialUpdateCount: Int = -1,
        val prepareFailure: SQLException? = null,
        val executeFailure: SQLException? = null,
        val resultSetCloseFailure: SQLException? = null,
        val statementCloseFailure: SQLException? = null,
    )

    private class FakeJdbc(scripts: List<SqlScript>) {
        private val scriptQueue = ArrayDeque(scripts)
        private var lastUpdateCount = -1
        var onConnectionClose: (Connection) -> Unit = {}
        val sql = mutableListOf<String>()
        val boundValues = mutableListOf<Any?>()
        var prepareCount = 0
            private set
        var statementCloseCount = 0
            private set
        var resultSetCloseCount = 0
            private set
        var connectionCloseCount = 0
            private set
        var abortCount = 0
            private set
        var commitCount = 0
            private set
        var rollbackCount = 0
            private set
        var autoCommitChangeCount = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    prepareCount++
                    val statementSql = args!![0] as String
                    sql += statementSql
                    val script = scriptQueue.removeFirst()
                    script.prepareFailure?.let { throw it }
                    statement(statementSql, script)
                }
                "close" -> {
                    connectionCloseCount++
                    onConnectionClose(proxy as Connection)
                    null
                }
                "abort" -> { abortCount++; null }
                "getAutoCommit" -> true
                "setAutoCommit" -> { autoCommitChangeCount++; null }
                "commit" -> { commitCount++; null }
                "rollback" -> { rollbackCount++; null }
                "isClosed" -> connectionCloseCount > 0
                "toString" -> "FakeOperationLockConnection"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                else -> jdbcDefaultValue(method.returnType)
            }
        } as Connection

        private fun statement(statementSql: String, script: SqlScript): PreparedStatement {
            val rows = resultSet(script)
            return Proxy.newProxyInstance(
                PreparedStatement::class.java.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "setObject" -> { boundValues += args!![1]; null }
                    "setNull" -> { boundValues += null; null }
                    "execute" -> {
                        script.executeFailure?.let { throw it }
                        lastUpdateCount = script.initialUpdateCount
                        script.executeReturnsResultSet
                    }
                    "getResultSet" -> rows
                    "getUpdateCount" -> lastUpdateCount
                    "getMoreResults" -> {
                        check(args!![0] == Statement.CLOSE_CURRENT_RESULT)
                        rows.close()
                        false
                    }
                    "close" -> {
                        statementCloseCount++
                        script.statementCloseFailure?.let { throw it }
                        null
                    }
                    "isClosed" -> false
                    "toString" -> "FakeOperationLockStatement($statementSql)"
                    else -> jdbcDefaultValue(method.returnType)
                }
            } as PreparedStatement
        }

        private fun resultSet(script: SqlScript): ResultSet {
            var consumed = false
            var closed = false
            val metadata = Proxy.newProxyInstance(
                ResultSetMetaData::class.java.classLoader,
                arrayOf(ResultSetMetaData::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "getColumnCount" -> 1
                    else -> jdbcDefaultValue(method.returnType)
                }
            } as ResultSetMetaData

            return Proxy.newProxyInstance(
                ResultSet::class.java.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "getMetaData" -> metadata
                    "next" -> if (consumed) false else { consumed = true; true }
                    "getObject" -> {
                        check(args!![0] == 1)
                        script.status
                    }
                    "close" -> if (!closed) {
                        closed = true
                        resultSetCloseCount++
                        script.resultSetCloseFailure?.let { throw it }
                        null
                    } else {
                        null
                    }
                    "isClosed" -> closed
                    else -> jdbcDefaultValue(method.returnType)
                }
            } as ResultSet
        }
    }
}

private fun jdbcDefaultValue(type: Class<*>): Any? = when (type) {
    java.lang.Boolean.TYPE -> false
    java.lang.Byte.TYPE -> 0.toByte()
    java.lang.Short.TYPE -> 0.toShort()
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Float.TYPE -> 0.0f
    java.lang.Double.TYPE -> 0.0
    java.lang.Character.TYPE -> '\u0000'
    else -> null
}
