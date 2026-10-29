package gog.my_project.data_base.query.executer.manager

import gog.my_project.collection.ICollection
import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.then
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TransactionExecutionTest {

    @Test
    fun `executor without scoped support fails closed without invoking callback or query execution`() = withConfig {
        var queryExecutions = 0
        val unsupported = object : IQueryBuilderExecutor {
            override fun execute(
                queryBuilder: gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi,
                blockExecute: (ExecuteResult<ResultSet>) -> Unit,
                blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
            ) {
                queryExecutions++
                error("Unsupported transaction must not execute a query")
            }

            override fun execute(
                queryBuilder: gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi,
                blockExecute: (ExecuteResult<Long>) -> Unit,
                blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
            ) {
                queryExecutions++
                error("Unsupported transaction must not execute a query")
            }

            override fun execute(
                queryBuilder: gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi,
                blockExecute: (ExecuteResult<Int>) -> Unit,
                blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
            ) {
                queryExecutions++
                error("Unsupported transaction must not execute a query")
            }

            override fun execute(
                queryBuilder: gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi,
                blockExecute: (ExecuteResult<Int>) -> Unit,
                blockQueryInfo: ((String?, MutableMap<String, Any?>) -> Unit)?,
            ) {
                queryExecutions++
                error("Unsupported transaction must not execute a query")
            }
        }
        var callbackCount = 0

        val failure = assertIs<ExecuteResult.Failure>(unsupported.transaction {
            callbackCount++
            ExecuteResult.Success(Unit)
        })

        assertIs<UnsupportedOperationException>(failure.exception)

        val connectionFailure = assertIs<ExecuteResult.Failure>(unsupported.connection {
            callbackCount++
            ExecuteResult.Success(Unit)
        })
        assertIs<UnsupportedOperationException>(connectionFailure.exception)
        assertEquals(0, callbackCount)
        assertEquals(0, queryExecutions)
    }

    @Test
    fun `successful callback commits once and preserves its result instance`() = withConfig {
        val jdbc = JdbcScript()
        val executor = QueryBuilderExecutor { jdbc.openConnection() }
        val expected = ExecuteResult.Success("done")
        var callbackCount = 0

        val actual = executor.transaction {
            callbackCount++
            expected
        }

        assertSame(expected, actual)
        assertEquals(1, callbackCount)
        assertEquals(1, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
        assertTrue(jdbc.events.indexOf("autoCommit:false") < jdbc.events.indexOf("commit"))
    }

    @Test
    fun `connection scope shares one connection and preserves query binding order without transaction control`() = withConfig {
        val jdbc = JdbcScript()
        var callbackCount = 0
        val expected = ExecuteResult.Success("done")

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection { ctx ->
            callbackCount++
            assertIs<ExecuteResult.Success<List<QueryRow>>>(ctx.queryBuilder(selectQuery()).execute())
            assertIs<ExecuteResult.Success<Long>>(ctx.queryBuilder(insertQuery(11)).execute())
            assertIs<ExecuteResult.Success<Int>>(ctx.queryBuilder(updateQuery(22)).execute())
            assertIs<ExecuteResult.Success<Int>>(ctx.queryBuilder(deleteQuery(33)).execute())
            expected
        }

        assertSame(expected, result)
        assertEquals(1, callbackCount)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(4, jdbc.preparedStatements)
        assertEquals(listOf(emptyList(), listOf(11), listOf(22), listOf(33)), jdbc.bindings.map { statement ->
            statement.map { it[1] }
        })
        assertEquals(4, jdbc.events.count { it == "statement.close" })
        assertEquals(1, jdbc.events.count { it == "resultSet.close" })
        assertEquals(1, jdbc.events.count { it == "generatedKeys.close" })
        assertTrue(jdbc.events.none { it.startsWith("autoCommit:") })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope latches query failure and rejects later queries without rollback`() = withConfig {
        val jdbc = JdbcScript(failExecutionAt = 1)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection { ctx ->
            ctx.queryBuilder(updateQuery(11)).execute() // Deliberately ignored.
            val rejected = ctx.queryBuilder(updateQuery(22)).execute()
            assertSame(jdbc.queryFailure, assertIs<ExecuteResult.Failure>(rejected).exception)
            ExecuteResult.Success("callback ignored the query failure")
        }

        assertSame(jdbc.queryFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope latches select validation failure before dispatch`() = withConfig {
        val jdbc = JdbcScript()
        val invalidQuery = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableAttribute("1") }
                    execute(DataType.INT)
                }
            }
            .from { table("records") }

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection { ctx ->
            val invalid = assertIs<ExecuteResult.Failure>(ctx.queryBuilder(invalidQuery).execute())
            val later = assertIs<ExecuteResult.Failure>(ctx.queryBuilder(updateQuery(11)).execute())
            assertSame(invalid.exception, later.exception)
            ExecuteResult.Success("callback ignored validation failure")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("alias"))
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope latches typed mapping failure and prevents later queries`() = withConfig {
        val jdbc = JdbcScript(selectRows = 2)
        val mapperFailure = IllegalArgumentException("invalid typed output")
        lateinit var mappingFailure: Throwable

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection { ctx ->
            val mapped = ctx.queryBuilder(selectQuery()).executeAs<Int> { throw mapperFailure }
            mappingFailure = assertIs<ExecuteResult.Failure>(mapped).exception
            assertSame(mapperFailure, mappingFailure.cause)

            val later = assertIs<ExecuteResult.Failure>(ctx.queryBuilder(updateQuery(22)).execute())
            assertSame(mappingFailure, later.exception)
            ExecuteResult.Success("callback ignored typed mapping failure")
        }

        assertSame(mappingFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope returns callback exception and closes once without transaction control`() = withConfig {
        val jdbc = JdbcScript(failClose = true)
        val callbackFailure = IllegalStateException("connection callback failed")
        var callbackCount = 0

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection<String> {
            callbackCount++
            throw callbackFailure
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertTrue(callbackFailure.suppressed.any { it === jdbc.closeFailure })
        assertEquals(1, callbackCount)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope preserves callback Failure and suppresses close failure`() = withConfig {
        val jdbc = JdbcScript(failClose = true)
        val expectedFailure = IllegalArgumentException("callback returned Failure")
        val expected = ExecuteResult.Failure(expectedFailure)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection<String> { expected }

        assertSame(expected, result)
        assertTrue(expectedFailure.suppressed.any { it === jdbc.closeFailure })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection close failure after success reports that no transaction outcome was applied`() = withConfig {
        val jdbc = JdbcScript(failClose = true)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection {
            ExecuteResult.Success("callback completed")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("without commit or rollback"))
        assertTrue(failure.message.orEmpty().contains("do not retry blindly"))
        assertSame(jdbc.closeFailure, failure.cause)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection cleanup does not hide callback Error`() = withConfig {
        val closeError = AssertionError("connection close failed fatally")
        val jdbc = JdbcScript(failCloseError = closeError)
        val callbackError = AssertionError("connection callback failed fatally")

        val thrown = assertFailsWith<AssertionError> {
            QueryBuilderExecutor { jdbc.openConnection() }.connection<String> {
                throw callbackError
            }
        }

        assertSame(callbackError, thrown)
        assertTrue(thrown.suppressed.any { it === closeError })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scopes reject nesting and cross nesting before invoking nested callbacks`() = withConfig {
        val jdbc = JdbcScript()
        var nestedCallbacks = 0

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection { ctx ->
            val nestedConnection = ctx.connection {
                nestedCallbacks++
                ExecuteResult.Success(Unit)
            }
            val nestedTransaction = ctx.transaction {
                nestedCallbacks++
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(nestedConnection)
            assertIs<ExecuteResult.Failure>(nestedTransaction)
            ExecuteResult.Success("outer callback ignored nested-scope failures")
        }

        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("Nested"))
        assertEquals(0, nestedCallbacks)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope inside transaction is rejected and latches failure in transaction`() = withConfig {
        val jdbc = JdbcScript()
        var connectionCallbackCount = 0

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            val nested = tx.connection {
                connectionCallbackCount++
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(nested)
            ExecuteResult.Success("transaction callback ignored nested connection scope")
        }

        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("Nested"))
        assertEquals(0, connectionCallbackCount)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope rejects root executor and executeOut bypasses`() = withConfig {
        val jdbc = JdbcScript()
        val executor = QueryBuilderExecutor { jdbc.openConnection() }

        val rootQueryResult = executor.connection { ctx ->
            val attempted = executor.queryBuilder(updateQuery(11)).execute()
            assertIs<ExecuteResult.Failure>(attempted)
            ExecuteResult.Success("root executor attempt was caught")
        }

        assertIs<ExecuteResult.Failure>(rootQueryResult)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })

        val outScript = JdbcScript()
        val outExecutor = QueryBuilderExecutor { outScript.openConnection() }
        val outResult = outExecutor.connection {
            assertFailsWith<UnsupportedOperationException> {
                outExecutor.executeOut(selectQuery(), blockResult = { _, _ -> })
            }
            ExecuteResult.Success("executeOut attempt was caught")
        }

        assertTrue(assertIs<ExecuteResult.Failure>(outResult).exception.message.orEmpty().contains("executeOut"))
        assertEquals(1, outScript.connectionCreations)
        assertEquals(0, outScript.preparedStatements)
        assertEquals(0, outScript.events.count { it == "commit" })
        assertEquals(0, outScript.events.count { it == "rollback" })
        assertEquals(1, outScript.events.count { it == "connection.close" })
    }

    @Test
    fun `retained connection consumer cannot execute after scope ends`() = withConfig {
        val jdbc = JdbcScript()
        lateinit var retained: gog.my_project.data_base.query.executer.result.UpdateQueryConsumer

        val result = QueryBuilderExecutor { jdbc.openConnection() }.connection { ctx ->
            retained = ctx.queryBuilder(updateQuery(11))
            ExecuteResult.Success("connection scope ended")
        }

        assertIs<ExecuteResult.Success<String>>(result)
        val afterScope = assertIs<ExecuteResult.Failure>(retained.execute())
        assertTrue(afterScope.exception is IllegalStateException)
        assertTrue(afterScope.exception.message.orEmpty().contains("scope has ended"))
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection scope rejects query from non owner thread without dispatching or closing early`() = withConfig {
        val jdbc = JdbcScript()
        val executor = QueryBuilderExecutor { jdbc.openConnection() }
        lateinit var retained: gog.my_project.data_base.query.executer.result.UpdateQueryConsumer
        var workerResult: ExecuteResult<Int>? = null
        var workerThrowable: Throwable? = null
        var wrongThreadFailure: Throwable? = null
        val workerDone = CountDownLatch(1)

        val scopeResult = executor.connection { ctx ->
            retained = ctx.queryBuilder(updateQuery(11))
            assertIs<ExecuteResult.Success<Int>>(retained.execute())

            val worker = Thread({
                try {
                    workerResult = retained.execute()
                } catch (failure: Throwable) {
                    workerThrowable = failure
                } finally {
                    workerDone.countDown()
                }
            }, "connection-scope-non-owner")
            worker.isDaemon = true
            worker.start()

            val completed = workerDone.await(5, TimeUnit.SECONDS)
            if (!completed) {
                worker.interrupt()
                worker.join(1_000)
            }
            assertTrue(completed, "Non-owner query attempt did not return within the deadline")
            assertNull(workerThrowable)

            val rejected = assertIs<ExecuteResult.Failure>(assertNotNull(workerResult))
            wrongThreadFailure = assertIs<IllegalStateException>(rejected.exception)
            assertTrue(wrongThreadFailure!!.message.orEmpty().contains("scope callback thread"))
            assertEquals(1, jdbc.preparedStatements)
            assertEquals(0, jdbc.events.count { it == "connection.close" })

            val ownerAfterRejection = assertIs<ExecuteResult.Failure>(retained.execute())
            assertSame(wrongThreadFailure, ownerAfterRejection.exception)
            assertEquals(1, jdbc.preparedStatements)
            assertEquals(0, jdbc.events.count { it == "connection.close" })
            ExecuteResult.Success("scope callback completed")
        }

        assertSame(wrongThreadFailure, assertIs<ExecuteResult.Failure>(scopeResult).exception)
        val afterScope = assertIs<ExecuteResult.Failure>(retained.execute())
        assertTrue(afterScope.exception is IllegalStateException)
        assertTrue(afterScope.exception.message.orEmpty().contains("scope has ended"))
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `scope start fails fast while a root query is in flight`() {
        val queryEntered = CountDownLatch(1)
        val releaseQuery = CountDownLatch(1)
        val jdbc = JdbcScript(
            executeUpdateEntered = queryEntered,
            releaseExecuteUpdate = releaseQuery,
        )
        withDriverManager(jdbc) {
        var factoryCalls = 0
        val executor = QueryBuilderExecutor {
            factoryCalls++
            jdbc.openConnection()
        }
        var queryResult: ExecuteResult<Int>? = null
        var queryThrowable: Throwable? = null
        val queryDone = CountDownLatch(1)
        val queryThread = Thread({
            try {
                queryResult = executor.queryBuilder(updateQuery(31)).execute()
            } catch (failure: Throwable) {
                queryThrowable = failure
            } finally {
                queryDone.countDown()
            }
        }, "root-query-before-scope")
        queryThread.isDaemon = true
        queryThread.start()

        try {
            assertTrue(
                queryEntered.await(5, TimeUnit.SECONDS),
                "Root query did not reach JDBC; result=$queryResult; throwable=$queryThrowable",
            )
            var callbackCount = 0
            val scopeResult = executor.transaction {
                callbackCount++
                ExecuteResult.Success("must not run")
            }

            val rejection = assertIs<ExecuteResult.Failure>(scopeResult).exception
            assertIs<IllegalStateException>(rejection)
            assertTrue(rejection.message.orEmpty().contains("root query is in progress"))
            assertEquals(0, callbackCount)
            assertEquals(0, factoryCalls)
            assertEquals(1, jdbc.connectionCreations)
            assertEquals(1, jdbc.preparedStatements)
        } finally {
            releaseQuery.countDown()
            queryThread.join(5_000)
        }

        assertTrue(queryDone.count == 0L, "Root query did not finish after its gate was released")
        assertNull(queryThrowable)
        assertEquals(1, assertIs<ExecuteResult.Success<Int>>(assertNotNull(queryResult)).result)

        val afterRace = assertRootSuccess(executor.queryBuilder(updateQuery(32)).execute(), "query after race")
        assertEquals(1, afterRace.result)
        assertEquals(0, factoryCalls)
        assertEquals(2, jdbc.connectionCreations)
        assertEquals(2, jdbc.preparedStatements)
        assertEquals(2, jdbc.events.count { it == "connection.close" })

        val scopeAfterQuery = executor.connection { ExecuteResult.Success("scope starts after query release") }
        assertEquals("scope starts after query release", assertIs<ExecuteResult.Success<String>>(scopeAfterQuery).result)
        assertEquals(1, factoryCalls)
        assertEquals(3, jdbc.connectionCreations)
        assertEquals(3, jdbc.events.count { it == "connection.close" })
        }
    }

    @Test
    fun `root query and executeOut are rejected during starting before a connection is opened`() = withDriverManager { jdbc ->
        val factoryEntered = CountDownLatch(1)
        val releaseFactory = CountDownLatch(1)
        var factoryCalls = 0
        val executor = QueryBuilderExecutor {
            factoryCalls++
            if (factoryCalls == 1) {
                factoryEntered.countDown()
                if (!releaseFactory.await(5, TimeUnit.SECONDS)) {
                    throw SQLException("Timed out waiting to release the scope connection factory")
                }
            }
            jdbc.openConnection()
        }
        var scopeResult: ExecuteResult<String>? = null
        var scopeThrowable: Throwable? = null
        var callbackCount = 0
        val scopeDone = CountDownLatch(1)
        val scopeThread = Thread({
            try {
                scopeResult = executor.connection {
                    callbackCount++
                    ExecuteResult.Success("callback entered")
                }
            } catch (failure: Throwable) {
                scopeThrowable = failure
            } finally {
                scopeDone.countDown()
            }
        }, "connection-scope-starting")
        scopeThread.isDaemon = true
        scopeThread.start()

        try {
            assertTrue(factoryEntered.await(5, TimeUnit.SECONDS), "Scope did not enter Starting")
            val queryResult = executor.queryBuilder(updateQuery(41)).execute()
            assertIs<IllegalStateException>(assertIs<ExecuteResult.Failure>(queryResult).exception)
            assertFailsWith<UnsupportedOperationException> {
                executor.executeOut(selectQuery(), blockResult = { _, _ -> error("executeOut must not dispatch") })
            }
            var nestedCallbackCount = 0
            val nested = executor.transaction {
                nestedCallbackCount++
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(nested)
            assertEquals(0, nestedCallbackCount)
            assertEquals(0, callbackCount)
            assertEquals(0, jdbc.connectionCreations)
            assertEquals(0, jdbc.preparedStatements)
            assertEquals(1, factoryCalls)
        } finally {
            releaseFactory.countDown()
            scopeThread.join(5_000)
        }

        assertTrue(scopeDone.count == 0L, "Starting scope did not finish after its factory was released")
        assertNull(scopeThrowable)
        val failure = assertIs<ExecuteResult.Failure>(assertNotNull(scopeResult))
        assertTrue(failure.exception.message.orEmpty().contains("managed execution scope is active"))
        assertEquals(1, callbackCount)
        assertEquals(1, factoryCalls)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(1, jdbc.events.count { it == "connection.close" })

        val afterScope = assertRootSuccess(executor.queryBuilder(updateQuery(42)).execute(), "query after Starting")
        assertEquals(1, afterScope.result)
        assertEquals(1, factoryCalls)
        assertEquals(2, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
    }

    @Test
    fun `root executor from non owner thread is rejected during active connection scope`() = withDriverManager { jdbc ->
        val executor = QueryBuilderExecutor { jdbc.openConnection() }
        var workerResult: ExecuteResult<Int>? = null
        var workerThrowable: Throwable? = null
        var executeOutThrowable: Throwable? = null
        val workerDone = CountDownLatch(1)

        val scopeResult = executor.connection {
            val worker = Thread({
                try {
                    workerResult = executor.queryBuilder(updateQuery(22)).execute()
                    try {
                        executor.executeOut(selectQuery(), blockResult = { _, _ -> error("executeOut must not dispatch") })
                    } catch (failure: Throwable) {
                        executeOutThrowable = failure
                    }
                } catch (failure: Throwable) {
                    workerThrowable = failure
                } finally {
                    workerDone.countDown()
                }
            }, "connection-scope-root-executor-non-owner")
            worker.isDaemon = true
            worker.start()

            val completed = workerDone.await(5, TimeUnit.SECONDS)
            if (!completed) {
                worker.interrupt()
                worker.join(1_000)
            }
            assertTrue(completed, "Root executor attempt did not return within the deadline")
            assertNull(workerThrowable)
            assertIs<UnsupportedOperationException>(executeOutThrowable)
            val rejection = assertIs<ExecuteResult.Failure>(assertNotNull(workerResult)).exception
            assertIs<IllegalStateException>(rejection)
            assertTrue(rejection.message.orEmpty().contains("managed execution scope is active"))

            assertEquals(1, jdbc.connectionCreations)
            assertEquals(0, jdbc.preparedStatements)
            assertEquals(0, jdbc.events.count { it == "connection.close" })
            ExecuteResult.Success("scope callback returns success after a rejected root query")
        }

        assertSame(assertIs<ExecuteResult.Failure>(scopeResult).exception, assertIs<ExecuteResult.Failure>(workerResult).exception)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })

        val afterScope = assertRootSuccess(executor.queryBuilder(updateQuery(23)).execute(), "query after active connection scope")
        assertEquals(1, afterScope.result)
        assertEquals(2, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(2, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `root executor from non owner thread is rejected during active transaction`() = withDriverManager { jdbc ->
        val executor = QueryBuilderExecutor { jdbc.openConnection() }
        var workerResult: ExecuteResult<Int>? = null
        var workerThrowable: Throwable? = null
        var executeOutThrowable: Throwable? = null
        val workerDone = CountDownLatch(1)

        val transactionResult = executor.transaction {
            val worker = Thread({
                try {
                    workerResult = executor.queryBuilder(updateQuery(22)).execute()
                    try {
                        executor.executeOut(selectQuery(), blockResult = { _, _ -> error("executeOut must not dispatch") })
                    } catch (failure: Throwable) {
                        executeOutThrowable = failure
                    }
                } catch (failure: Throwable) {
                    workerThrowable = failure
                } finally {
                    workerDone.countDown()
                }
            }, "transaction-root-executor-non-owner")
            worker.isDaemon = true
            worker.start()

            val completed = workerDone.await(5, TimeUnit.SECONDS)
            if (!completed) {
                worker.interrupt()
                worker.join(1_000)
            }
            assertTrue(completed, "Root executor attempt did not return within the deadline")
            assertNull(workerThrowable)
            assertIs<UnsupportedOperationException>(executeOutThrowable)
            val rejection = assertIs<ExecuteResult.Failure>(assertNotNull(workerResult)).exception
            assertIs<IllegalStateException>(rejection)
            assertTrue(rejection.message.orEmpty().contains("managed execution scope is active"))
            assertEquals(1, jdbc.connectionCreations)
            assertEquals(0, jdbc.preparedStatements)
            assertEquals(0, jdbc.events.count { it == "connection.close" })
            ExecuteResult.Success("transaction callback returns success after a rejected root query")
        }

        assertSame(
            assertIs<ExecuteResult.Failure>(transactionResult).exception,
            assertIs<ExecuteResult.Failure>(workerResult).exception,
        )
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })

        assertRootSuccess(executor.queryBuilder(updateQuery(23)).execute(), "query after active transaction")
        assertEquals(2, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(2, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `sealed connection scope rejects late root calls without changing its result`() {
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val jdbc = JdbcScript(
            connectionCloseEntered = closeEntered,
            releaseConnectionClose = releaseClose,
        )
        withDriverManager(jdbc) {
        var factoryCalls = 0
        val executor = QueryBuilderExecutor {
            factoryCalls++
            jdbc.openConnection()
        }
        var scopeResult: ExecuteResult<String>? = null
        var scopeThrowable: Throwable? = null
        var scopeCallbackCount = 0
        val scopeDone = CountDownLatch(1)
        val scopeThread = Thread({
            try {
                scopeResult = executor.connection {
                    scopeCallbackCount++
                    ExecuteResult.Success("connection result was sealed")
                }
            } catch (failure: Throwable) {
                scopeThrowable = failure
            } finally {
                scopeDone.countDown()
            }
        }, "connection-scope-finalizing")
        scopeThread.isDaemon = true
        scopeThread.start()

        try {
            assertTrue(closeEntered.await(5, TimeUnit.SECONDS), "Connection scope did not enter close")
            val lateQuery = assertIs<ExecuteResult.Failure>(executor.queryBuilder(updateQuery(51)).execute())
            assertIs<IllegalStateException>(lateQuery.exception)
            assertFailsWith<UnsupportedOperationException> {
                executor.executeOut(selectQuery(), blockResult = { _, _ -> error("executeOut must not dispatch") })
            }
            var lateScopeCallbackCount = 0
            val lateScope = executor.transaction {
                lateScopeCallbackCount++
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(lateScope)
            assertEquals(0, lateScopeCallbackCount)
            assertEquals(1, scopeCallbackCount)
            assertEquals(1, factoryCalls)
            assertEquals(1, jdbc.connectionCreations)
            assertEquals(0, jdbc.preparedStatements)
        } finally {
            releaseClose.countDown()
            scopeThread.join(5_000)
        }

        assertTrue(scopeDone.count == 0L, "Connection scope did not finish after close was released")
        assertNull(scopeThrowable)
        assertEquals("connection result was sealed", assertIs<ExecuteResult.Success<String>>(scopeResult).result)
        assertEquals(1, jdbc.events.count { it == "connection.close" })

        val afterScope = assertRootSuccess(executor.queryBuilder(updateQuery(52)).execute(), "query after connection finalization")
        assertEquals(1, afterScope.result)
        assertEquals(1, factoryCalls)
        assertEquals(2, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        }
    }

    @Test
    fun `sealed transaction rejects late root calls and independent roots remain available`() {
        val commitEntered = CountDownLatch(1)
        val releaseCommit = CountDownLatch(1)
        val jdbc = JdbcScript(commitEntered = commitEntered, releaseCommit = releaseCommit)
        withDriverManager(jdbc) {
        var factoryCalls = 0
        val executor = QueryBuilderExecutor {
            factoryCalls++
            jdbc.openConnection()
        }
        var scopeResult: ExecuteResult<String>? = null
        var scopeThrowable: Throwable? = null
        var scopeCallbackCount = 0
        val scopeDone = CountDownLatch(1)
        val scopeThread = Thread({
            try {
                scopeResult = executor.transaction {
                    scopeCallbackCount++
                    ExecuteResult.Success("transaction result was sealed")
                }
            } catch (failure: Throwable) {
                scopeThrowable = failure
            } finally {
                scopeDone.countDown()
            }
        }, "transaction-scope-finalizing")
        scopeThread.isDaemon = true
        scopeThread.start()

        try {
            assertTrue(commitEntered.await(5, TimeUnit.SECONDS), "Transaction did not enter commit")
            val lateQuery = assertIs<ExecuteResult.Failure>(executor.queryBuilder(updateQuery(61)).execute())
            assertIs<IllegalStateException>(lateQuery.exception)
            assertFailsWith<UnsupportedOperationException> {
                executor.executeOut(selectQuery(), blockResult = { _, _ -> error("executeOut must not dispatch") })
            }
            var lateScopeCallbackCount = 0
            val lateScope = executor.connection {
                lateScopeCallbackCount++
                ExecuteResult.Success(Unit)
            }
            assertIs<ExecuteResult.Failure>(lateScope)
            assertEquals(0, lateScopeCallbackCount)

            val independentJdbc = JdbcScript()
            val independentExecutor = QueryBuilderExecutor { independentJdbc.openConnection() }
            val independentResult = assertIs<ExecuteResult.Success<String>>(
                independentExecutor.connection { ExecuteResult.Success("independent root available") },
            )
            assertEquals("independent root available", independentResult.result)
            assertEquals(1, independentJdbc.connectionCreations)
            assertEquals(0, independentJdbc.preparedStatements)

            assertEquals(1, scopeCallbackCount)
            assertEquals(1, factoryCalls)
            assertEquals(1, jdbc.connectionCreations)
            assertEquals(0, jdbc.preparedStatements)
            assertEquals(0, jdbc.events.count { it == "connection.close" })
        } finally {
            releaseCommit.countDown()
            scopeThread.join(5_000)
        }

        assertTrue(scopeDone.count == 0L, "Transaction did not finish after commit was released")
        assertNull(scopeThrowable)
        assertEquals("transaction result was sealed", assertIs<ExecuteResult.Success<String>>(scopeResult).result)
        assertEquals(1, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })

        val afterScope = assertRootSuccess(executor.queryBuilder(updateQuery(63)).execute(), "query after transaction finalization")
        assertEquals(1, afterScope.result)
        assertEquals(1, factoryCalls)
        assertEquals(2, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        }
    }

    @Test
    fun `connection acquisition failure does not invoke callback`() = withConfig {
        val expected = SQLException("connection acquisition failed")
        var callbackCount = 0

        val result = QueryBuilderExecutor { throw expected }.connection {
            callbackCount++
            ExecuteResult.Success(Unit)
        }

        assertSame(expected, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, callbackCount)
    }

    @Test
    fun `query failure rolls back and remains the transaction failure`() = withConfig {
        val jdbc = JdbcScript(failExecutionAt = 1)
        val executor = QueryBuilderExecutor { jdbc.openConnection() }

        val result = executor.transaction { tx ->
            tx.queryBuilder(updateQuery(11)).execute()
        }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertSame(jdbc.queryFailure, failure.exception)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `ignored query failure is latched and later queries are rejected`() = withConfig {
        val jdbc = JdbcScript(failExecutionAt = 1)
        val executor = QueryBuilderExecutor { jdbc.openConnection() }

        val result = executor.transaction { tx ->
            tx.queryBuilder(updateQuery(11)).execute() // Deliberately ignored.
            val rejected = tx.queryBuilder(updateQuery(22)).execute()
            assertSame(jdbc.queryFailure, assertIs<ExecuteResult.Failure>(rejected).exception)
            ExecuteResult.Success("callback ignored the failure")
        }

        assertSame(jdbc.queryFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
    }

    @Test
    fun `callback failure result rolls back and preserves its throwable`() = withConfig {
        val jdbc = JdbcScript()
        val callbackFailure = IllegalArgumentException("business rule failed")

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction<String> {
            ExecuteResult.Failure(callbackFailure)
        }

        assertSame(callbackFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `intentional fluent chain failure is returned to transaction and rolls back`() = withConfig {
        val jdbc = JdbcScript()
        val expected = IllegalStateException("business rule rejected update")
        var successCalls = 0
        var thenCalls = 0
        var errorCalls = 0

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            tx.queryBuilder(updateQuery(11))
                .execute()
                .success { affectedRows ->
                    successCalls++
                    "updated:$affectedRows"
                }
                .then { mapped ->
                    thenCalls++
                    assertEquals("updated:1", mapped)
                    ExecuteResult.Failure(expected)
                }
                .error { failure ->
                    errorCalls++
                    assertSame(expected, failure)
                }
        }

        assertSame(expected, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, successCalls)
        assertEquals(1, thenCalls)
        assertEquals(1, errorCalls)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `callback exception becomes failure and rolls back once`() = withConfig {
        val jdbc = JdbcScript()
        val callbackException = IllegalStateException("callback threw")
        var callbackCount = 0

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction<String> {
            callbackCount++
            throw callbackException
        }

        assertSame(callbackException, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, callbackCount)
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `toValue failure inside transaction preserves throwable and rolls back`() = withConfig {
        val jdbc = JdbcScript()
        val original = IllegalArgumentException("extracted query failure")

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction<String> {
            ExecuteResult.Failure(original).toValue()
        }

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `connection acquisition and transaction start failures do not invoke callback`() = withConfig {
        val acquisitionFailure = IllegalStateException("connection unavailable")
        var callbackCount = 0
        val acquisitionResult = QueryBuilderExecutor { throw acquisitionFailure }.transaction {
            callbackCount++
            ExecuteResult.Success(Unit)
        }
        assertSame(acquisitionFailure, assertIs<ExecuteResult.Failure>(acquisitionResult).exception)
        assertEquals(0, callbackCount)

        val jdbc = JdbcScript(failAutoCommit = true)
        val startResult = QueryBuilderExecutor { jdbc.openConnection() }.transaction {
            callbackCount++
            ExecuteResult.Success(Unit)
        }
        assertSame(jdbc.beginFailure, assertIs<ExecuteResult.Failure>(startResult).exception)
        assertEquals(0, callbackCount)
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `commit failure is reported and rollback is attempted`() = withConfig {
        val jdbc = JdbcScript(failCommit = true)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction {
            ExecuteResult.Success("commit me")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        val commitFailure = assertIs<TransactionCommitException>(failure)
        assertSame(jdbc.commitFailure, commitFailure.cause)
        assertTrue(commitFailure.message.orEmpty().contains("rollback was attempted"))
        assertEquals(1, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `rollback failure is suppressed without replacing query failure`() = withConfig {
        val jdbc = JdbcScript(failExecutionAt = 1, failRollback = true)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            tx.queryBuilder(updateQuery(11)).execute()
            ExecuteResult.Success(Unit)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(jdbc.queryFailure, failure)
        assertTrue(failure.suppressed.any { it === jdbc.rollbackFailure })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `rollback and close failures retain the query failure and cleanup order`() = withConfig {
        val jdbc = JdbcScript(failExecutionAt = 1, failRollback = true, failClose = true)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            tx.queryBuilder(updateQuery(11)).execute()
            ExecuteResult.Success(Unit)
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertSame(jdbc.queryFailure, failure)
        assertEquals(2, failure.suppressed.size)
        assertSame(jdbc.rollbackFailure, failure.suppressed[0])
        assertSame(jdbc.closeFailure, failure.suppressed[1])
        assertEquals(
            listOf("rollback", "connection.close"),
            jdbc.events.filter { it == "rollback" || it == "connection.close" },
        )
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.connectionCreations)
    }

    @Test
    fun `rollback Error remains fatal and retains query and later close failures`() = withConfig {
        val rollbackError = Error("rollback fatal error")
        val jdbc = JdbcScript(
            failExecutionAt = 1,
            rollbackError = rollbackError,
            failClose = true,
        )

        val thrown = assertFailsWith<Error> {
            QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
                tx.queryBuilder(updateQuery(11)).execute()
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(rollbackError, thrown)
        assertEquals(2, thrown.suppressed.size)
        assertSame(jdbc.queryFailure, thrown.suppressed[0])
        assertSame(jdbc.closeFailure, thrown.suppressed[1])
        assertEquals(
            listOf("rollback", "connection.close"),
            jdbc.events.filter { it == "rollback" || it == "connection.close" },
        )
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.connectionCreations)
    }

    @Test
    fun `callback Error remains primary when rollback Error and close failure occur`() = withConfig {
        val callbackError = Error("callback fatal error")
        val rollbackError = Error("rollback fatal error")
        val jdbc = JdbcScript(rollbackError = rollbackError, failClose = true)

        val thrown = assertFailsWith<Error> {
            QueryBuilderExecutor { jdbc.openConnection() }.transaction<Unit> {
                throw callbackError
            }
        }

        assertSame(callbackError, thrown)
        assertEquals(2, thrown.suppressed.size)
        assertSame(rollbackError, thrown.suppressed[0])
        assertSame(jdbc.closeFailure, thrown.suppressed[1])
        assertEquals(
            listOf("rollback", "connection.close"),
            jdbc.events.filter { it == "rollback" || it == "connection.close" },
        )
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.connectionCreations)
    }

    @Test
    fun `close failure after commit reports committed outcome without rollback`() = withConfig {
        val jdbc = JdbcScript(failClose = true)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction {
            ExecuteResult.Success("committed")
        }

        val closeFailure = assertIs<TransactionCompletionException>(
            assertIs<ExecuteResult.Failure>(result).exception,
        )
        assertTrue(closeFailure.committed)
        assertSame(jdbc.closeFailure, closeFailure.cause)
        assertEquals(1, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `nullable success payload is returned without transformation`() = withConfig {
        val jdbc = JdbcScript()
        val expected = ExecuteResult.Success<String>(null)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { expected }

        assertSame(expected, result)
        assertIs<ExecuteResult.Success<String>>(result)
        assertEquals(1, jdbc.events.count { it == "commit" })
    }

    @Test
    fun `select insert update and delete execute through one connection in binding order`() = withConfig {
        val jdbc = JdbcScript()
        var connections = 0
        val executor = QueryBuilderExecutor {
            connections++
            jdbc.openConnection()
        }

        val result = executor.transaction { tx ->
            val first = tx.queryBuilder(insertQuery(11)).execute()
            val selected = tx.queryBuilder(selectQuery()).execute()
            val second = tx.queryBuilder(updateQuery(22)).execute()
            val third = tx.queryBuilder(deleteQuery(33)).execute()
            if (first is ExecuteResult.Failure) first
            else if (selected is ExecuteResult.Failure) selected
            else if (second is ExecuteResult.Failure) second
            else if (third is ExecuteResult.Failure) third
            else ExecuteResult.Success("all operations ran")
        }

        assertIs<ExecuteResult.Success<String>>(result)
        assertEquals(1, connections)
        assertEquals(4, jdbc.preparedStatements)
        assertEquals(listOf(listOf(11), emptyList(), listOf(22), listOf(33)), jdbc.bindings.map { statement ->
            statement.map { it[1] }
        })
        assertEquals(4, jdbc.events.count { it == "statement.close" })
        assertEquals(1, jdbc.events.count { it == "generatedKeys.close" })
        assertEquals(1, jdbc.events.count { it == "resultSet.close" })
        assertEquals(1, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `retained transaction consumer cannot execute after scope ends`() = withConfig {
        val jdbc = JdbcScript()
        lateinit var retained: gog.my_project.data_base.query.executer.result.UpdateQueryConsumer
        val executor = QueryBuilderExecutor { jdbc.openConnection() }

        assertIs<ExecuteResult.Success<String>>(executor.transaction { tx ->
            retained = tx.queryBuilder(updateQuery(11))
            ExecuteResult.Success("scope ended")
        })

        val afterScope = assertIs<ExecuteResult.Failure>(retained.execute())
        assertTrue(afterScope.exception is IllegalStateException)
        assertTrue(afterScope.exception.message.orEmpty().contains("scope has ended"))
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `nested transaction is rejected and cannot be ignored into commit`() = withConfig {
        val jdbc = JdbcScript()
        val executor = QueryBuilderExecutor { jdbc.openConnection() }
        val otherExecutor = QueryBuilderExecutor { error("Nested transaction must not request another connection") }
        var nestedCallbackCount = 0

        val result = executor.transaction { tx ->
            val nested = tx.transaction {
                nestedCallbackCount++
                ExecuteResult.Success("must not execute")
            }
            assertTrue(assertIs<ExecuteResult.Failure>(nested).exception.message.orEmpty().contains("Nested"))
            val nestedOnOtherManager = otherExecutor.transaction {
                nestedCallbackCount++
                ExecuteResult.Success("also must not execute")
            }
            assertTrue(
                assertIs<ExecuteResult.Failure>(nestedOnOtherManager).exception.message.orEmpty().contains("Nested"),
            )
            ExecuteResult.Success("outer ignored nested rejection")
        }

        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("Nested"))
        assertEquals(0, nestedCallbackCount)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
    }

    @Test
    fun `select materializes and closes result set before commit`() = withConfig {
        val jdbc = JdbcScript(selectRows = 2)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            val rows = tx.queryBuilder(selectQuery()).execute()
            when (rows) {
                is ExecuteResult.Failure -> rows
                is ExecuteResult.Success -> ExecuteResult.Success(rows.result)
            }
        }

        val rows = assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result
        assertEquals(listOf(42, 42), rows?.map { it["value"] })
        assertTrue(jdbc.events.indexOf("resultSet.close") < jdbc.events.indexOf("statement.close"))
        assertTrue(jdbc.events.indexOf("statement.close") < jdbc.events.indexOf("commit"))
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `empty select result remains an empty materialized list`() = withConfig {
        val jdbc = JdbcScript(selectRows = 0)

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            tx.queryBuilder(selectQuery()).execute()
        }

        assertEquals(emptyList(), assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result)
        assertEquals(1, jdbc.events.count { it == "commit" })
    }

    @Test
    fun `select materialization failure is latched even when callback ignores it`() = withConfig {
        val jdbc = JdbcScript(selectColumnLabel = "unexpected_alias")

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            tx.queryBuilder(selectQuery()).execute()
            ExecuteResult.Success("ignored mapping failure")
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("does not match SELECT output alias"))
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `typed select mapping succeeds once and keeps the typed collection`() = withConfig {
        val jdbc = JdbcScript(selectRows = 2)
        var mapperCalls = 0

        val result: ExecuteResult<ICollection<Int>> =
            QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
                tx.queryBuilder(selectQuery()).executeAs { row ->
                    mapperCalls++
                    row.getValue("value") as Int
                }
            }

        val values = assertNotNull(assertIs<ExecuteResult.Success<ICollection<Int>>>(result).result)
        assertEquals(listOf(42, 42), values.toList())
        assertEquals(2, mapperCalls)
        assertEquals(1, jdbc.preparedStatements)
        assertTrue(jdbc.events.indexOf("resultSet.close") < jdbc.events.indexOf("statement.close"))
        assertTrue(jdbc.events.indexOf("statement.close") < jdbc.events.indexOf("commit"))
        assertEquals(1, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `typed mapping failure is latched when ignored and prevents later queries`() = withConfig {
        val jdbc = JdbcScript(selectRows = 2)
        val mapperFailure = IllegalArgumentException("invalid typed output")
        lateinit var mappingFailure: Throwable

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            val mapped = tx.queryBuilder(selectQuery()).executeAs<Int> { throw mapperFailure }
            mappingFailure = assertIs<ExecuteResult.Failure>(mapped).exception
            assertTrue(mappingFailure.message.orEmpty().contains("row 1"))
            assertSame(mapperFailure, mappingFailure.cause)

            val rejected = tx.queryBuilder(updateQuery(22)).execute()
            assertSame(mappingFailure, assertIs<ExecuteResult.Failure>(rejected).exception)
            ExecuteResult.Success("callback ignored the typed mapping failure")
        }

        assertSame(mappingFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(1, jdbc.preparedStatements)
        assertTrue(jdbc.events.indexOf("resultSet.close") < jdbc.events.indexOf("statement.close"))
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `typed mapper Error remains fatal and is rethrown after rollback and cleanup`() = withConfig {
        val jdbc = JdbcScript()
        val fatal = AssertionError("fatal typed mapper error")

        val thrown = assertFailsWith<AssertionError> {
            QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
                tx.queryBuilder(selectQuery()).executeAs<Int> { throw fatal }
                ExecuteResult.Success("must not commit")
            }
        }

        assertSame(fatal, thrown)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `sql and details callbacks do not execute a query`() = withConfig {
        val jdbc = JdbcScript()
        var sqlCallbacks = 0
        var detailsCallbacks = 0

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            tx.queryBuilder(selectQuery())
                .sql { rendered ->
                    sqlCallbacks++
                    assertTrue(rendered.query.orEmpty().isNotBlank())
                }
                .details { metadata ->
                    detailsCallbacks++
                    assertNotNull(metadata)
                }
            ExecuteResult.Success("inspected")
        }

        assertIs<ExecuteResult.Success<String>>(result)
        assertEquals(1, sqlCallbacks)
        assertEquals(1, detailsCallbacks)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(1, jdbc.events.count { it == "commit" })
    }

    @Test
    fun `callback Error rolls back and is rethrown unchanged`() = withConfig {
        val jdbc = JdbcScript()
        val fatal = AssertionError("fatal callback error")

        val thrown = assertFailsWith<AssertionError> {
            QueryBuilderExecutor { jdbc.openConnection() }.transaction<String> {
                throw fatal
            }
        }

        assertSame(fatal, thrown)
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `query Error cannot be hidden as an ordinary Failure`() = withConfig {
        val jdbc = JdbcScript(failExecutionAt = 1, executionError = AssertionError("fatal JDBC path"))
        val fatal = jdbc.executionError as AssertionError

        val thrown = assertFailsWith<AssertionError> {
            QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
                tx.queryBuilder(updateQuery(11)).execute() // QueryConsumer captures Throwable.
                ExecuteResult.Success("must not commit")
            }
        }

        assertSame(fatal, thrown)
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `executeOut is rejected inside transaction instead of opening an independent connection`() = withConfig {
        val jdbc = JdbcScript()

        val result = QueryBuilderExecutor { jdbc.openConnection() }.transaction { tx ->
            assertFailsWith<UnsupportedOperationException> {
                tx.executeOut(selectQuery(), blockResult = { _, _ -> })
            }
            ExecuteResult.Success("attempt was caught")
        }

        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("executeOut"))
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
    }

    @Test
    fun `original executor cannot run an independent query inside transaction`() = withConfig {
        val jdbc = JdbcScript()
        val executor = QueryBuilderExecutor { jdbc.openConnection() }

        val result = executor.transaction {
            val queryResult = executor.queryBuilder(updateQuery(11)).execute()
            assertTrue(
                assertIs<ExecuteResult.Failure>(queryResult).exception.message.orEmpty()
                    .contains("executor passed to the active scope callback"),
            )
            ExecuteResult.Success("the rejected query is latched")
        }

        assertTrue(
            assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty()
                .contains("executor passed to the active scope callback"),
        )
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `executeOut through original executor cannot bypass transaction`() = withConfig {
        val jdbc = JdbcScript()
        val executor = QueryBuilderExecutor { jdbc.openConnection() }

        val result = executor.transaction {
            assertFailsWith<UnsupportedOperationException> {
                executor.executeOut(selectQuery(), blockResult = { _, _ -> })
            }
            ExecuteResult.Success("the rejected executeOut is latched")
        }

        assertTrue(assertIs<ExecuteResult.Failure>(result).exception.message.orEmpty().contains("executeOut"))
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(0, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(1, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `ordinary query execution still owns and closes its independent connection`() = withDriverManager { jdbc ->
        val result = QueryBuilderExecutor().queryBuilder(updateQuery(33)).execute()

        assertEquals(1, assertIs<ExecuteResult.Success<Int>>(result).result)
        assertEquals(1, jdbc.connectionCreations)
        assertEquals(1, jdbc.preparedStatements)
        assertEquals(0, jdbc.events.count { it == "commit" })
        assertEquals(0, jdbc.events.count { it == "rollback" })
        assertEquals(1, jdbc.events.count { it == "connection.close" })
    }

    @Test
    fun `root operation Error releases its admission permit`() = withDriverManager(
        JdbcScript(
            failExecutionAt = 1,
            executionError = AssertionError("root query failed fatally"),
        ),
    ) { failingJdbc ->
        val fatal = failingJdbc.executionError as AssertionError
        val recoveredJdbc = JdbcScript()
        var factoryCalls = 0
        val executor = QueryBuilderExecutor {
            factoryCalls++
            recoveredJdbc.openConnection()
        }

        val rootFailure = assertIs<ExecuteResult.Failure>(executor.queryBuilder(updateQuery(71)).execute())
        assertSame(fatal, rootFailure.exception)
        assertEquals(1, failingJdbc.events.count { it == "connection.close" })

        val scopeResult = executor.connection { ExecuteResult.Success("permit released") }
        assertEquals("permit released", assertIs<ExecuteResult.Success<String>>(scopeResult).result)
        assertEquals(1, factoryCalls)
        assertEquals(1, recoveredJdbc.connectionCreations)
        assertEquals(1, recoveredJdbc.events.count { it == "connection.close" })
        assertEquals(0, recoveredJdbc.events.count { it == "commit" })
        assertEquals(0, recoveredJdbc.events.count { it == "rollback" })
    }

    private fun updateQuery(value: Int) = QueryRenderUpdateBuilder()
        .table { table("records") }
        .addValue {
            column("amount")
            setValue {
                paramName("amount_$value")
                paramValue(value)
            }
        }

    private fun deleteQuery(value: Int) = QueryRenderDeleteBuilder()
        .table { table("records") }
        .where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("id") }
                    operationEqual()
                    sideValueParam {
                        paramName("id_$value")
                        paramValue(value)
                    }
                }
            }
        }

    private fun insertQuery(value: Int) = QueryRenderInsertBuilder()
        .table { table("records") }
        .addValue {
            column("amount")
            setValue {
                paramName("amount_$value")
                paramValue(value)
            }
        }

    private fun selectQuery() = QueryRenderSelectBuilder()
        .select {
            addColumn {
                column { tableAttribute("1") }
                alias("value")
                execute(DataType.INT)
            }
        }
        .from { table("records").alias("r") }

    private fun assertRootSuccess(result: ExecuteResult<Int>, operation: String): ExecuteResult.Success<Int> {
        if (result is ExecuteResult.Failure) {
            throw AssertionError("$operation failed: ${result.exception}", result.exception)
        }
        return assertIs<ExecuteResult.Success<Int>>(result)
    }

    private inline fun <T> withConfig(block: () -> T): T {
        val previous = DefaultDatabaseConfig.config
        DefaultDatabaseConfig.config = DatabaseConfig(
            dbDomain = "jdbc:transaction-scope",
            dbPort = null,
            dbName = "test",
            dbUserName = "test",
            dbPassword = "",
        )
        return try {
            block()
        } finally {
            DefaultDatabaseConfig.config = previous
        }
    }

    private fun <T> withDriverManager(
        jdbc: JdbcScript = JdbcScript(),
        block: (JdbcScript) -> T,
    ): T = withConfig {
        val driver = RecordingDriver(jdbc)
        DriverManager.registerDriver(driver)
        try {
            block(jdbc)
        } finally {
            DriverManager.deregisterDriver(driver)
        }
    }

    private inner class JdbcScript(
        private val failAutoCommit: Boolean = false,
        private val failExecutionAt: Int? = null,
        private val failCommit: Boolean = false,
        private val failRollback: Boolean = false,
        private val rollbackError: Error? = null,
        private val failClose: Boolean = false,
        private val failCloseError: Error? = null,
        private val selectRows: Int = 1,
        private val selectColumnLabel: String = "value",
        val executionError: Error? = null,
        private val executeUpdateEntered: CountDownLatch? = null,
        private val releaseExecuteUpdate: CountDownLatch? = null,
        private val commitEntered: CountDownLatch? = null,
        private val releaseCommit: CountDownLatch? = null,
        private val connectionCloseEntered: CountDownLatch? = null,
        private val releaseConnectionClose: CountDownLatch? = null,
    ) {
        val events = mutableListOf<String>()
        val bindings = mutableListOf<MutableList<List<Any?>>>()
        var connectionCreations = 0
        var preparedStatements = 0
        val queryFailure = SQLException("query execution failed")
        val beginFailure = SQLException("could not begin transaction")
        val commitFailure = SQLException("commit failed")
        val rollbackFailure = SQLException("rollback failed")
        val closeFailure = SQLException("connection close failed")

        fun openConnection(): Connection {
            connectionCreations++
            var autoCommit = true
            var closed = false
            return proxy(Connection::class.java) { _, method, args ->
                when (method.name) {
                    "setAutoCommit" -> {
                        val next = args?.get(0) as Boolean
                        events += "autoCommit:$next"
                        if (failAutoCommit) throw beginFailure
                        autoCommit = next
                        null
                    }
                    "getAutoCommit" -> autoCommit
                    "prepareStatement" -> {
                        preparedStatements++
                        val statementIndex = preparedStatements
                        events += "prepare:${args?.get(0)}"
                        bindings.add(mutableListOf())
                        statementProxy(statementIndex)
                    }
                    "commit" -> {
                        events += "commit"
                        commitEntered?.countDown()
                        awaitRelease(releaseCommit, "transaction commit")
                        if (failCommit) throw commitFailure
                        null
                    }
                    "rollback" -> {
                        events += "rollback"
                        rollbackError?.let { throw it }
                        if (failRollback) throw rollbackFailure
                        null
                    }
                    "close" -> {
                        events += "connection.close"
                        connectionCloseEntered?.countDown()
                        awaitRelease(releaseConnectionClose, "connection close")
                        closed = true
                        failCloseError?.let { throw it }
                        if (failClose) throw closeFailure
                        null
                    }
                    "isClosed" -> closed
                    "isValid" -> !closed
                    else -> defaultValue(method.returnType)
                }
            }
        }

        private fun statementProxy(statementIndex: Int): PreparedStatement =
            proxy(PreparedStatement::class.java) { _, method, args ->
                when (method.name) {
                    "setObject", "setNull", "setInt", "setLong", "setString" -> {
                        bindings[statementIndex - 1] += args.orEmpty().toList()
                        null
                    }
                    "executeUpdate" -> {
                        executeUpdateEntered?.countDown()
                        awaitRelease(releaseExecuteUpdate, "statement execution")
                        if (statementIndex == failExecutionAt) {
                            executionError?.let { throw it }
                            throw queryFailure
                        }
                        1
                    }
                    "executeQuery" -> resultSet(selectRows, "resultSet.close")
                    "getGeneratedKeys" -> resultSet(1, "generatedKeys.close")
                    "close" -> { events += "statement.close"; null }
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            }

        private fun resultSet(rowCount: Int, closeEvent: String): ResultSet {
            var row = 0
            return proxy(ResultSet::class.java) { _, method, _ ->
                when (method.name) {
                    "next" -> row++ < rowCount
                    "getInt" -> 42
                    "getLong" -> 9001L
                    "getString" -> "value"
                    "wasNull" -> false
                    "getMetaData" -> resultSetMetadata()
                    "close" -> { events += closeEvent; null }
                    else -> defaultValue(method.returnType)
                }
            }
        }

        private fun resultSetMetadata(): ResultSetMetaData = proxy(ResultSetMetaData::class.java) { _, method, _ ->
            when (method.name) {
                "getColumnCount" -> 1
                "getColumnLabel", "getColumnName" -> selectColumnLabel
                else -> defaultValue(method.returnType)
            }
        }

        private fun awaitRelease(latch: CountDownLatch?, operation: String) {
            if (latch != null && !latch.await(5, TimeUnit.SECONDS)) {
                throw SQLException("Timed out waiting to release $operation")
            }
        }
    }

    private class RecordingDriver(private val jdbc: JdbcScript) : Driver {
        override fun connect(url: String?, info: Properties?): Connection? =
            if (url != null && acceptsURL(url)) jdbc.openConnection() else null

        override fun acceptsURL(url: String?): Boolean = url?.startsWith("jdbc:transaction-scope/") == true
        override fun getPropertyInfo(url: String?, info: Properties?) = emptyArray<java.sql.DriverPropertyInfo>()
        override fun getMajorVersion() = 1
        override fun getMinorVersion() = 0
        override fun jdbcCompliant() = false
        override fun getParentLogger(): Logger = Logger.getLogger("transaction-test")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, handler: InvocationHandler): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T

    private fun defaultValue(type: Class<*>): Any? = when (type) {
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
}
