package gog.my_project.data_base_manager.facade

import gog.my_project.data_base_operation.executor.events.OperationDatabaseEvent
import gog.my_project.data_base_operation.executor.events.OperationDatabaseEventType
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_operation.then.ConnectionDecision
import gog.my_project.data_base_operation.then.OperationPipelineDefinition
import gog.my_project.data_base_operation.then.PipelineCompletion
import gog.my_project.data_base_query.executor.interfaces.IQueryBuilderExecutor
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.SQLException
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DatabaseHandleOperationScopeTest {
    private object Primary : DatabaseKey

    @Test
    fun `watch is handle scoped, does not open a connection, and observes the managed operation`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val events = mutableListOf<OperationDatabaseEvent>()

        try {
            val subscription = handle.watch { event ->
                if (event is OperationDatabaseEvent) events += event
            }
            assertEquals(0, provider.openCount.get())

            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertEquals(
                listOf(
                    OperationDatabaseEventType.CONNECTION_OPENED,
                    OperationDatabaseEventType.CONNECTION_CLOSED,
                ),
                events.map(OperationDatabaseEvent::type),
            )

            subscription.close()
            events.clear()
            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertTrue(events.isEmpty())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `concurrent operations on one handle keep distinct identities through cleanup`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val events = CopyOnWriteArrayList<OperationDatabaseEvent>()
        val workers = Executors.newFixedThreadPool(2)
        val enteredCallbacks = CountDownLatch(2)
        val releaseCallbacks = CountDownLatch(1)
        val subscription = handle.watch { event ->
            if (event is OperationDatabaseEvent) events += event
        }

        try {
            val operations = List(2) {
                workers.submit(Callable {
                    handle.withOperationScope { scope ->
                        val operationId = scope.operationId
                        enteredCallbacks.countDown()
                        if (!releaseCallbacks.await(5, TimeUnit.SECONDS)) {
                            return@withOperationScope ExecuteResult.Failure(
                                IllegalStateException("Timed out waiting to release the concurrent Operation"),
                            )
                        }
                        ExecuteResult.Success(operationId)
                    }
                })
            }

            assertTrue(enteredCallbacks.await(5, TimeUnit.SECONDS))
            releaseCallbacks.countDown()
            val results = operations.map {
                assertIs<ExecuteResult.Success<String?>>(it.get(5, TimeUnit.SECONDS)).result
            }
            val operationIds = results.map { requireNotNull(it) }
            assertEquals(2, operationIds.distinct().size)
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())

            operationIds.forEach { operationId ->
                val lifecycle = events.filter { it.operationId == operationId }.map(OperationDatabaseEvent::type)
                assertEquals(
                    listOf(
                        OperationDatabaseEventType.CONNECTION_OPENED,
                        OperationDatabaseEventType.CONNECTION_CLOSED,
                    ),
                    lifecycle,
                )
            }
        } finally {
            releaseCallbacks.countDown()
            subscription.close()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun `multiple watchers are isolated and closing one leaves the other active`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var firstCount = 0
        var secondCount = 0

        try {
            val first = handle.watch {
                firstCount++
                throw AssertionError("observer failure")
            }
            val second = handle.watch {
                secondCount++
            }

            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertTrue(firstCount > 0)
            assertTrue(secondCount > 0)

            first.close()
            val previousSecondCount = secondCount
            assertIs<ExecuteResult.Success<*>>(handle.withOperationScope {
                ExecuteResult.Success(Unit)
            })
            assertEquals(previousSecondCount + 2, secondCount)
            second.close()
        } finally {
            manager.close()
        }
    }

    @Test
    fun `scope API exposes the managed root scope and connection through callback`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var callbackScope: OperationExecutionScope? = null

        try {
            val result = handle.withOperationScope { scope ->
                provider.events += "callback-start"
                callbackScope = scope
                assertSame(provider.connections.single(), scope.connection)
                assertNull(scope.rejectOperation())
                provider.events += "callback-end"
                ExecuteResult.Success(scope)
            }

            val success = assertIs<ExecuteResult.Success<*>>(result)
            assertSame(callbackScope, success.result)
            assertEquals(listOf("callback-start", "callback-end", "connection-close"), provider.events)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(0, provider.autoCommitChanges.get())
            assertEquals(0, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
            assertTrue(provider.connections.single().isClosed)
        } finally {
            manager.close()
        }
    }

    @Test
    fun `root scope can start a child transaction on the same managed connection`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]

        try {
            val result = handle.withOperationScope { rootScope ->
                val rootConnection = rootScope.connection
                val transactionResult = rootScope.transaction { childScope ->
                    assertSame(rootConnection, childScope.connection)
                    ExecuteResult.Success(Unit)
                }
                assertIs<ExecuteResult.Success<*>>(transactionResult)
                ExecuteResult.Success(Unit)
            }

            assertEquals(ExecuteResult.Success(Unit), result)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `callback failure remains primary when connection cleanup also fails`() {
        val cleanupFailure = SQLException("synthetic close failure")
        val provider = TrackingProvider(closeFailure = cleanupFailure)
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val callbackFailure = IllegalStateException("synthetic callback failure")
        var callbackCount = 0

        try {
            val result = handle.withOperationScope<Unit> {
                callbackCount++
                throw callbackFailure
            }

            val failure = assertIs<ExecuteResult.Failure>(result)
            assertSame(callbackFailure, failure.exception)
            assertSame(cleanupFailure, callbackFailure.suppressed.single())
            assertEquals(1, callbackCount)
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `cleanup failure after successful callback is returned and handle remains reusable`() {
        val provider = TrackingProvider(closeFailure = SQLException("synthetic close failure"))
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var callbackCount = 0

        try {
            val first = handle.withOperationScope {
                callbackCount++
                ExecuteResult.Success("completed")
            }
            assertIs<ExecuteResult.Failure>(first)

            val second = handle.withOperationScope {
                callbackCount++
                ExecuteResult.Success("completed again")
            }
            assertIs<ExecuteResult.Failure>(second)

            assertEquals(2, callbackCount)
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `fatal callback error propagates after connection cleanup`() {
        val cleanupFailure = SQLException("synthetic close failure")
        val provider = TrackingProvider(closeFailure = cleanupFailure)
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val fatal = AssertionError("synthetic fatal error")
        var callbackCount = 0

        try {
            val thrown = assertFailsWith<AssertionError> {
                handle.withOperationScope<Unit> {
                    callbackCount++
                    throw fatal
                }
            }

            assertSame(fatal, thrown)
            assertSame(cleanupFailure, fatal.suppressed.single())
            assertEquals(1, callbackCount)
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `existing connection and transaction APIs retain their behavior`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]

        try {
            assertEquals(
                ExecuteResult.Success("connection"),
                handle.connection { ExecuteResult.Success("connection") },
            )
            assertEquals(
                ExecuteResult.Success("transaction"),
                handle.transaction { ExecuteResult.Success("transaction") },
            )

            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `legacy custom handle compiles and keeps old APIs without the new capability`() {
        val legacy = LegacyHandle()

        assertEquals(ExecuteResult.Success("legacy connection"), legacy.connection {
            ExecuteResult.Success("legacy connection")
        })
        assertEquals(ExecuteResult.Success("legacy transaction"), legacy.transaction {
            ExecuteResult.Success("legacy transaction")
        })
        val unsupported = assertIs<ExecuteResult.Failure>(legacy.withOperationScope {
            ExecuteResult.Success(Unit)
        })
        assertIs<UnsupportedOperationException>(unsupported.exception)
        assertFailsWith<UnsupportedOperationException> {
            legacy.watch { }
        }
        assertFalse(DatabaseHandleApi::class.java.methods.any { it.name == "withOperationScope" })
    }

    @Test
    fun `concurrent scope operations remain independently admitted`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val bothEntered = CountDownLatch(2)
        val continueCallbacks = CountDownLatch(1)
        val workers = Executors.newFixedThreadPool(2)

        try {
            val results = (1..2).map {
                workers.submit<ExecuteResult<Int>> {
                    handle.withOperationScope {
                        bothEntered.countDown()
                        check(bothEntered.await(5, TimeUnit.SECONDS))
                        check(continueCallbacks.await(5, TimeUnit.SECONDS))
                        ExecuteResult.Success(1)
                    }
                }
            }

            assertTrue(bothEntered.await(5, TimeUnit.SECONDS))
            continueCallbacks.countDown()
            results.forEach { assertEquals(ExecuteResult.Success(1), it.get(5, TimeUnit.SECONDS)) }
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            continueCallbacks.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun `connection pipeline uses one manager owned connection across stages`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val seenContexts = mutableListOf<Any>()

        try {
            val pipeline = handle.connectionPipeline<String> { context ->
                seenContexts += context
                context.next("connected").connection(ConnectionDecision.REUSE)
            }.then<Int> { value, context ->
                seenContexts += context
                context.next(value.length).connection(ConnectionDecision.REUSE)
            }.then<String> { length, context ->
                seenContexts += context
                "value:$length"
            }

            val result = pipeline.execute()

            assertEquals(
                PipelineCompletion.Current("value:9"),
                assertIs<ExecuteResult.Success<PipelineCompletion<PipelineCompletion<String, Int>, String>>>(result).result,
            )
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(3, seenContexts.size)
            assertTrue(seenContexts.distinctBy(System::identityHashCode).size == 3)
            assertEquals(0, provider.autoCommitChanges.get())
            assertEquals(0, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `transaction pipeline uses one manager owned transaction lifecycle`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]

        try {
            val pipeline = handle.transactionPipeline<String> { context ->
                context.next("transaction")
            }.then<Int> { value, context ->
                context.next(value.length)
            }.then<String> { length, _ -> "value:$length" }

            val result = pipeline.execute()

            assertEquals(
                PipelineCompletion.Current("value:11"),
                assertIs<ExecuteResult.Success<PipelineCompletion<PipelineCompletion<String, Int>, String>>>(result).result,
            )
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(1, provider.autoCommitChanges.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `connection Definition runs typed stages through the managed handle`() {
        class State(var calls: Int = 0)

        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val observedStates = mutableListOf<State>()
        val definition = OperationPipelineDefinition.create<State, String>(stateFactory = { State() }) {
            start<Int> { context ->
                observedStates += context.state
                context.state.calls++
                7
            }.then<Int, String> { value, context ->
                observedStates += context.state
                context.state.calls++
                "${value + context.state.calls}"
            }
        }

        try {
            val result = handle.executeConnectionDefinition(definition)

            assertEquals("9", assertIs<ExecuteResult.Success<String>>(result).result)
            assertSame(observedStates[0], observedStates[1])
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(0, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `transaction Definition runs typed stages through the managed handle and commits`() {
        class State(var total: Int = 0)

        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val observedStates = mutableListOf<State>()
        val definition = OperationPipelineDefinition.create<State, String>(stateFactory = { State() }) {
            start<Int> { context ->
                observedStates += context.state
                context.state.total = 5
                4
            }.then<Int, String> { value, context ->
                observedStates += context.state
                context.state.total += value
                "total-${context.state.total}"
            }
        }

        try {
            val result = handle.executeTransactionDefinition(definition)

            assertEquals("total-9", assertIs<ExecuteResult.Success<String>>(result).result)
            assertSame(observedStates[0], observedStates[1])
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `same Handle Definition calls share Coordinator lineage`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val firstRecipeEntered = CountDownLatch(1)
        val releaseFirstRecipe = CountDownLatch(1)
        val recipeCalls = AtomicInteger()
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            if (recipeCalls.incrementAndGet() == 1) {
                firstRecipeEntered.countDown()
                check(releaseFirstRecipe.await(5, TimeUnit.SECONDS))
            }
            start { "completed" }
        }
        val workers = Executors.newSingleThreadExecutor()

        try {
            val first = workers.submit<ExecuteResult<String>> {
                handle.executeConnectionDefinition(definition)
            }
            assertTrue(firstRecipeEntered.await(5, TimeUnit.SECONDS))

            val rejected = assertIs<ExecuteResult.Failure>(handle.executeConnectionDefinition(definition))
            releaseFirstRecipe.countDown()
            val incumbent = assertIs<ExecuteResult.Failure>(first.get(5, TimeUnit.SECONDS))

            assertSame(rejected.exception, incumbent.exception)
            assertEquals(1, recipeCalls.get())
            assertEquals(0, provider.openCount.get())
        } finally {
            releaseFirstRecipe.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun `separate Handles do not share Definition Coordinator lineage`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val firstHandle: DatabaseHandleApi = manager[Primary]
        val secondHandle: DatabaseHandleApi = manager[Primary]
        val bothRecipesEntered = CountDownLatch(2)
        val releaseRecipes = CountDownLatch(1)
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            bothRecipesEntered.countDown()
            check(releaseRecipes.await(5, TimeUnit.SECONDS))
            start { "completed" }
        }
        val workers = Executors.newFixedThreadPool(2)

        try {
            val first = workers.submit<ExecuteResult<String>> {
                firstHandle.executeConnectionDefinition(definition)
            }
            val second = workers.submit<ExecuteResult<String>> {
                secondHandle.executeConnectionDefinition(definition)
            }

            assertTrue(bothRecipesEntered.await(5, TimeUnit.SECONDS))
            releaseRecipes.countDown()
            assertEquals("completed", assertIs<ExecuteResult.Success<String>>(first.get(5, TimeUnit.SECONDS)).result)
            assertEquals("completed", assertIs<ExecuteResult.Success<String>>(second.get(5, TimeUnit.SECONDS)).result)
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            releaseRecipes.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun `Definition preparation failure returns through ExecuteResult without opening connection`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val failure = IllegalArgumentException("invalid Definition")
        var stateFactoryCalls = 0
        val definition = OperationPipelineDefinition.create<Unit, String>(
            stateFactory = { stateFactoryCalls++; Unit },
        ) { throw failure }

        try {
            val result = handle.executeConnectionDefinition(definition)

            assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(0, stateFactoryCalls)
            assertEquals(0, provider.openCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `transaction Definition failure rolls back and does not commit`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val failure = IllegalStateException("transaction Definition failed")
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start<Int> { 1 }.then<Int, String> { _, context ->
                context.failed<String>(failure)
                "ignored result"
            }
        }

        try {
            val result = handle.executeTransactionDefinition(definition)

            assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(0, provider.commitCount.get())
            assertEquals(1, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `custom DatabaseHandleApi Definition execution preserves unsupported behavior`() {
        val handle = LegacyHandle()
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start { "must not execute" }
        }

        val connectionResult = handle.executeConnectionDefinition(definition)
        val transactionResult = handle.executeTransactionDefinition(definition)

        assertIs<UnsupportedOperationException>(assertIs<ExecuteResult.Failure>(connectionResult).exception)
        assertIs<UnsupportedOperationException>(assertIs<ExecuteResult.Failure>(transactionResult).exception)
    }

    @Test
    fun `same Handle Connection and Transaction Definitions share admission lineage`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val firstRecipeEntered = CountDownLatch(1)
        val releaseFirstRecipe = CountDownLatch(1)
        val recipeCalls = AtomicInteger()
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            if (recipeCalls.incrementAndGet() == 1) {
                firstRecipeEntered.countDown()
                check(releaseFirstRecipe.await(5, TimeUnit.SECONDS))
            }
            start { "completed" }
        }
        val workers = Executors.newSingleThreadExecutor()

        try {
            val connectionExecution = workers.submit<ExecuteResult<String>> {
                handle.executeConnectionDefinition(definition)
            }
            assertTrue(firstRecipeEntered.await(5, TimeUnit.SECONDS))

            val transactionRejection = assertIs<ExecuteResult.Failure>(
                handle.executeTransactionDefinition(definition),
            )
            releaseFirstRecipe.countDown()
            val connectionRejection = assertIs<ExecuteResult.Failure>(
                connectionExecution.get(5, TimeUnit.SECONDS),
            )

            assertSame(transactionRejection.exception, connectionRejection.exception)
            assertEquals(1, recipeCalls.get())
            assertEquals(0, provider.openCount.get())
        } finally {
            releaseFirstRecipe.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun `same Thread Definition and legacy Pipeline nesting is rejected by OperationScopeGuard`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var nestedFailure: Throwable? = null
        val legacyPipeline = handle.connectionPipeline<String> { context ->
            context.next("legacy").connection(ConnectionDecision.REUSE)
        }
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start { _ ->
                nestedFailure = assertIs<ExecuteResult.Failure>(legacyPipeline.execute()).exception
                "definition-result"
            }
        }

        try {
            val result = handle.executeConnectionDefinition(definition)

            assertSame(nestedFailure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `different Threads Definition and legacy Pipeline remain independently admitted`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val definitionStageEntered = CountDownLatch(1)
        val legacyStageEntered = CountDownLatch(1)
        val releaseStages = CountDownLatch(1)
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start {
                definitionStageEntered.countDown()
                check(legacyStageEntered.await(5, TimeUnit.SECONDS))
                check(releaseStages.await(5, TimeUnit.SECONDS))
                "definition-result"
            }
        }
        val legacyPipeline = handle.connectionPipeline<String> { _ ->
            legacyStageEntered.countDown()
            check(definitionStageEntered.await(5, TimeUnit.SECONDS))
            check(releaseStages.await(5, TimeUnit.SECONDS))
            "legacy-result"
        }
        val workers = Executors.newFixedThreadPool(2)

        try {
            val definitionExecution = workers.submit<ExecuteResult<String>> {
                handle.executeConnectionDefinition(definition)
            }
            assertTrue(definitionStageEntered.await(5, TimeUnit.SECONDS))
            val legacyExecution = workers.submit<ExecuteResult<*>> { legacyPipeline.execute() }
            assertTrue(legacyStageEntered.await(5, TimeUnit.SECONDS))
            releaseStages.countDown()

            assertEquals(
                "definition-result",
                assertIs<ExecuteResult.Success<String>>(definitionExecution.get(5, TimeUnit.SECONDS)).result,
            )
            assertIs<ExecuteResult.Success<*>>(legacyExecution.get(5, TimeUnit.SECONDS))
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
        } finally {
            releaseStages.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    @Test
    fun `same Thread Definition and legacy Transaction nesting is rejected by OperationScopeGuard`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        var nestedFailure: Throwable? = null
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start {
                nestedFailure = assertIs<ExecuteResult.Failure>(
                    handle.transaction { ExecuteResult.Success("nested-transaction") },
                ).exception
                "definition-result"
            }
        }

        try {
            val result = handle.executeConnectionDefinition(definition)

            assertSame(nestedFailure, assertIs<ExecuteResult.Failure>(result).exception)
            assertEquals(1, provider.openCount.get())
            assertEquals(1, provider.connectionCloseCount.get())
            assertEquals(0, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            manager.close()
        }
    }

    @Test
    fun `different Threads Definition and legacy Transaction remain independently admitted`() {
        val provider = TrackingProvider()
        val manager = manager(provider)
        val handle: DatabaseHandleApi = manager[Primary]
        val definitionStageEntered = CountDownLatch(1)
        val transactionCallbackEntered = CountDownLatch(1)
        val releaseOperations = CountDownLatch(1)
        val definition = OperationPipelineDefinition.create<Unit, String>(stateFactory = { Unit }) {
            start {
                definitionStageEntered.countDown()
                check(transactionCallbackEntered.await(5, TimeUnit.SECONDS))
                check(releaseOperations.await(5, TimeUnit.SECONDS))
                "definition-result"
            }
        }
        val workers = Executors.newFixedThreadPool(2)

        try {
            val definitionExecution = workers.submit<ExecuteResult<String>> {
                handle.executeConnectionDefinition(definition)
            }
            assertTrue(definitionStageEntered.await(5, TimeUnit.SECONDS))
            val legacyTransaction = workers.submit<ExecuteResult<String>> {
                handle.transaction {
                    transactionCallbackEntered.countDown()
                    check(releaseOperations.await(5, TimeUnit.SECONDS))
                    ExecuteResult.Success("transaction-result")
                }
            }
            assertTrue(transactionCallbackEntered.await(5, TimeUnit.SECONDS))
            releaseOperations.countDown()

            assertEquals(
                "definition-result",
                assertIs<ExecuteResult.Success<String>>(definitionExecution.get(5, TimeUnit.SECONDS)).result,
            )
            assertEquals(
                "transaction-result",
                assertIs<ExecuteResult.Success<String>>(legacyTransaction.get(5, TimeUnit.SECONDS)).result,
            )
            assertEquals(2, provider.openCount.get())
            assertEquals(2, provider.connectionCloseCount.get())
            assertEquals(1, provider.commitCount.get())
            assertEquals(0, provider.rollbackCount.get())
        } finally {
            releaseOperations.countDown()
            workers.shutdownNow()
            manager.close()
        }
    }

    private fun manager(provider: TrackingProvider): DatabaseManager {
        val databaseConfig = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://127.0.0.1",
                dbPort = 3306,
                dbName = "manager_scope_test",
                dbUserName = "user",
                dbPassword = "password",
            ),
        )
        return DatabaseManager(
            { database(Primary) { config = databaseConfig } },
            { name, config, identity -> DatabaseContext(name, config, provider, identity) },
        )
    }

    private class LegacyHandle : DatabaseHandleApi {
        override val key: DatabaseKey = Primary
        private val executor = Proxy.newProxyInstance(
            IQueryBuilderExecutor::class.java.classLoader,
            arrayOf(IQueryBuilderExecutor::class.java),
        ) { _, method, _ -> defaultValue(method.returnType) } as IQueryBuilderExecutor

        override fun <T> connection(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            block(executor)

        override fun <T> transaction(block: (IQueryBuilderExecutor) -> ExecuteResult<T>): ExecuteResult<T> =
            block(executor)
    }

    private class TrackingProvider(
        private val closeFailure: SQLException? = null,
    ) : IDatabaseConnection {
        val openCount = AtomicInteger()
        val connectionCloseCount = AtomicInteger()
        val autoCommitChanges = AtomicInteger()
        val commitCount = AtomicInteger()
        val rollbackCount = AtomicInteger()
        val connections = CopyOnWriteArrayList<Connection>()
        val events = CopyOnWriteArrayList<String>()

        override fun build(): Connection {
            openCount.incrementAndGet()
            val closed = AtomicBoolean(false)
            val connection = Proxy.newProxyInstance(
                Connection::class.java.classLoader,
                arrayOf(Connection::class.java),
            ) { proxy, method, args ->
                when (method.name) {
                    "close" -> {
                        connectionCloseCount.incrementAndGet()
                        events += "connection-close"
                        closed.set(true)
                        closeFailure?.let { throw it }
                        null
                    }
                    "isClosed" -> closed.get()
                    "getAutoCommit" -> true
                    "setAutoCommit" -> {
                        autoCommitChanges.incrementAndGet()
                        null
                    }
                    "commit" -> {
                        commitCount.incrementAndGet()
                        null
                    }
                    "rollback" -> {
                        rollbackCount.incrementAndGet()
                        null
                    }
                    "toString" -> "ManagerScopeTestConnection"
                    "hashCode" -> System.identityHashCode(proxy)
                    "equals" -> proxy === args?.firstOrNull()
                    else -> defaultValue(method.returnType)
                }
            } as Connection
            connections += connection
            return connection
        }
    }

    companion object {
        private fun defaultValue(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Double.TYPE -> 0.0
            java.lang.Float.TYPE -> 0.0f
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
