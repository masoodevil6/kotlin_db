package gog.my_project.data_base.manager.execute.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.query.BoundQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.Properties
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QueryExecuteContractTest {
    @Test
    fun bindsEveryRepeatedPlaceholderOccurrenceInOrder() = withDatabase(Script()) { script ->
        var callbackCount = 0
        QueryExecute().executeUpdate(
            BuiltQuery(
                "UPDATE t SET value = :id WHERE parent_id = :id AND tenant = :id2",
                mutableListOf(SqlParameter.of("id", 4L), SqlParameter.of("id2", 9)),
            ),
        ) {
            callbackCount++
            assertIs<ExecuteResult.Success<Int>>(it)
        }

        assertEquals(1, callbackCount)
        assertEquals("UPDATE t SET value = ? WHERE parent_id = ? AND tenant = ?", script.preparedSql)
        assertEquals(
            listOf<List<Any?>>(
                listOf(1, 4L, java.sql.Types.BIGINT),
                listOf(2, 4L, java.sql.Types.BIGINT),
                listOf(3, 9, java.sql.Types.INTEGER),
            ),
            script.bindings,
        )
    }

    @Test
    fun `bound select binds same-name independent occurrences by positional index`() = withDatabase(Script()) { script ->
        val first = SqlParameter("shared_value", 11, java.sql.Types.INTEGER)
        val second = SqlParameter("shared_value", 22, java.sql.Types.BIGINT)
        val third = SqlParameter("shared_value", 33, java.sql.Types.VARCHAR)
        val nullable = SqlParameter.nullOf("optional_value", java.sql.Types.VARCHAR)
        val occurrences = listOf(first, second, third, first, nullable)
        val boundQuery = BoundQuery("SELECT ?, ?, ?, ?, ?", occurrences)

        var callbackCount = 0
        QueryExecute().executeSelect(boundQuery) { result ->
            callbackCount++
            assertIs<ExecuteResult.Success<ResultSet>>(result).result?.close()
        }

        assertEquals(1, callbackCount)
        assertEquals("SELECT ?, ?, ?, ?, ?", script.preparedSql)
        assertEquals(
            listOf<List<Any?>>(
                listOf(1, 11, java.sql.Types.INTEGER),
                listOf(2, 22, java.sql.Types.BIGINT),
                listOf(3, 33, java.sql.Types.VARCHAR),
                listOf(4, 11, java.sql.Types.INTEGER),
                listOf(5, java.sql.Types.VARCHAR),
            ),
            script.bindings,
        )
        assertSame(first, boundQuery.parameterOccurrences[0])
        assertSame(first, boundQuery.parameterOccurrences[3])
    }

    @Test
    fun `bound query executes independent AST parameter bindings without legacy SqlParameter`() =
        withDatabase(Script()) { script ->
            val binding = QueryParameterBinding("ast_value", 41, java.sql.Types.INTEGER)
            var callbackCount = 0

            QueryExecute().executeUpdate(
                BoundQuery("UPDATE t SET value = ? WHERE id = ?", listOf(binding, binding)),
            ) { result ->
                callbackCount++
                assertIs<ExecuteResult.Success<Int>>(result)
            }

            assertEquals(1, callbackCount)
            assertEquals("UPDATE t SET value = ? WHERE id = ?", script.preparedSql)
            assertEquals(
                listOf<List<Any?>>(
                    listOf(1, 41, java.sql.Types.INTEGER),
                    listOf(2, 41, java.sql.Types.INTEGER),
                ),
                script.bindings,
            )
        }

    @Test
    fun mismatchesFailSynchronouslyBeforeConnectionAcquisition() = withDatabase(Script()) { script ->
        val invalidQueries = listOf(
            BuiltQuery("SELECT :id", mutableListOf()),
            BuiltQuery("SELECT 1", mutableListOf(SqlParameter.of("extra", 1))),
            BuiltQuery(
                "SELECT :id",
                mutableListOf(SqlParameter.of("id", 1), SqlParameter.of("id", 2)),
            ),
            BuiltQuery(
                "SELECT :id",
                mutableListOf(SqlParameter.of("id", 1), SqlParameter("id", 1, java.sql.Types.BIGINT)),
            ),
        )

        invalidQueries.forEach { query ->
            var callbackCount = 0
            assertFailsWith<IllegalArgumentException> {
                QueryExecute().executeUpdate(query) { callbackCount++ }
            }
            assertEquals(0, callbackCount)
        }
        assertEquals(0, script.connectionAttempts)
    }

    @Test
    fun duplicateEquivalentParameterObjectsUseOneCanonicalValue() = withDatabase(Script()) { script ->
        var callbackCount = 0
        QueryExecute().executeUpdate(
            BuiltQuery(
                "UPDATE t SET value = :id WHERE parent_id = :id",
                mutableListOf(SqlParameter.of("id", 5), SqlParameter.of("id", 5)),
            ),
        ) {
            callbackCount++
            assertIs<ExecuteResult.Success<Int>>(it)
        }
        assertEquals(1, callbackCount)
        assertEquals(
            listOf<List<Any?>>(
                listOf(1, 5, java.sql.Types.INTEGER),
                listOf(2, 5, java.sql.Types.INTEGER),
            ),
            script.bindings,
        )
    }

    @Test
    fun nullBlankAndPrepareFailureFollowTheContract() = withDatabase(Script(prepareFailure = SQLException("prepare failed"))) { script ->
        var nullResult: ExecuteResult<Int>? = null
        var nullCount = 0
        QueryExecute().executeUpdate(BuiltQuery(null, mutableListOf())) {
            nullCount++
            nullResult = it
        }
        assertEquals(1, nullCount)
        assertIs<ExecuteResult.Failure>(nullResult)
        assertEquals(0, script.prepareCount)

        var blankCount = 0
        var blankResult: ExecuteResult<Int>? = null
        QueryExecute().executeUpdate(BuiltQuery("   ", mutableListOf())) {
            blankCount++
            blankResult = it
        }
        assertEquals(1, blankCount)
        assertIs<ExecuteResult.Failure>(blankResult)
        assertEquals("   ", script.preparedSql)
    }

    @Test
    fun connectionAcquisitionFailurePropagatesWithoutCallback() {
        val script = Script(connectionFailure = SQLException("connection failed"))
        withDatabase(script) {
            var callbackCount = 0
            assertFailsWith<RuntimeException> {
                QueryExecute().executeUpdate(BuiltQuery("UPDATE t SET x = 1", mutableListOf())) {
                    callbackCount++
                }
            }
            assertEquals(0, callbackCount)
            assertEquals(1, script.connectionAttempts)
        }
    }

    @Test
    fun jdbcPrepareBindAndExecutionFailuresDeliverOneFailureCallback() {
        withDatabase(Script(prepareFailure = SQLException("prepare"))) {
            assertFailureOnce { callback ->
                QueryExecute().executeUpdate(BuiltQuery("UPDATE t SET x = 1", mutableListOf()), callback)
            }
        }
        withDatabase(Script(bindFailure = SQLException("bind"))) {
            assertFailureOnce { callback ->
                QueryExecute().executeUpdate(
                    BuiltQuery("UPDATE t SET x = :x", mutableListOf(SqlParameter.of("x", 1))), callback,
                )
            }
        }
        withDatabase(Script(queryFailure = SQLException("query"))) {
            assertFailureOnce { callback ->
                QueryExecute().executeSelect(BuiltQuery("SELECT 1", mutableListOf()), callback)
            }
        }
        withDatabase(Script(updateFailure = SQLException("update"))) {
            assertFailureOnce { callback ->
                QueryExecute().executeUpdate(BuiltQuery("UPDATE t SET x = 1", mutableListOf()), callback)
            }
        }
    }

    @Test
    fun consumerExceptionsPropagateWithoutCallbackReentry() = withDatabase(Script()) { _ ->
        for (failure in listOf(SQLException("consumer sql"), IllegalStateException("consumer runtime"))) {
            var callbackCount = 0
            assertFailsWith<Throwable> {
                QueryExecute().executeSelect(BuiltQuery("SELECT 1", mutableListOf())) { result ->
                    callbackCount++
                    assertIs<ExecuteResult.Success<ResultSet>>(result)
                    throw failure
                }
            }
            assertEquals(1, callbackCount)
        }
    }

    @Test
    fun selectResultSetIsLiveInsideCallbackAndCallerCanCloseIt() = withDatabase(Script()) { script ->
        var callbackCount = 0
        QueryExecute().executeSelect(BuiltQuery("SELECT 1", mutableListOf())) { result ->
            callbackCount++
            val rows = assertIs<ExecuteResult.Success<ResultSet>>(result).result
            assertNotNull(rows)
            assertTrue(!rows.isClosed)
            rows.close()
        }
        assertEquals(1, callbackCount)
        assertTrue(script.resultSets.single().closed)
    }

    @Test
    fun resultSetCloseFailureIsAConsumerExceptionWithoutCallbackReentry() = withDatabase(
        Script(resultSetCloseFailure = SQLException("result set close")),
    ) { script ->
        var callbackCount = 0
        assertFailsWith<SQLException> {
            QueryExecute().executeSelect(BuiltQuery("SELECT 1", mutableListOf())) { result ->
                callbackCount++
                assertIs<ExecuteResult.Success<ResultSet>>(result).result!!.close()
            }
        }
        assertEquals(1, callbackCount)
        assertTrue(script.resultSets.single().closed)
    }

    @Test
    fun insertSuccessReturnsGeneratedLongKey() = withDatabase(Script(updateCount = 1, generatedKey = 922337203685477000L)) { script ->
        var callbackCount = 0
        var result: ExecuteResult<Long>? = null
        QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf())) {
            callbackCount++
            result = it
            assertTrue(script.resultSets.single().closed)
        }
        assertEquals(1, callbackCount)
        assertEquals(ExecuteResult.Success(922337203685477000L), result)
    }

    @Test
    fun insertZeroRowsAndMissingKeyReturnOneFailure() {
        withDatabase(Script(updateCount = 0)) { zeroScript ->
            assertFailureOnce { callback ->
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf()), callback)
            }
            assertEquals(0, zeroScript.resultSets.size)
        }

        val noKey = Script(updateCount = 1, generatedKey = null)
        withDatabase(noKey) {
            assertFailureOnce { callback ->
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf()), callback)
            }
            assertTrue(noKey.resultSets.single().closed)
        }
    }

    @Test
    fun insertGeneratedKeyRetrievalAndCloseFailuresReturnFailureOnce() {
        withDatabase(Script(updateCount = 1, generatedKeysFailure = SQLException("generated keys"))) { retrievalFailure ->
            assertFailureOnce { callback ->
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf()), callback)
            }
            assertEquals(0, retrievalFailure.resultSets.size)
        }

        val closeFailure = Script(updateCount = 1, generatedKey = 8L, generatedKeysCloseFailure = SQLException("keys close"))
        withDatabase(closeFailure) {
            assertFailureOnce { callback ->
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf()), callback)
            }
            assertEquals(1, closeFailure.resultSets.size)
        }
    }

    @Test
    fun insertUpdateAndPostSuccessCloseFailuresDoNotRepeatCallback() {
        withDatabase(Script(updateFailure = SQLException("insert executeUpdate"))) {
            assertFailureOnce { callback ->
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf()), callback)
            }
        }

        withDatabase(Script(statementCloseFailure = SQLException("insert statement close"))) {
            var callbackCount = 0
            assertFailsWith<SQLException> {
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf())) {
                    callbackCount++
                    assertIs<ExecuteResult.Success<Long>>(it)
                }
            }
            assertEquals(1, callbackCount)
        }

        withDatabase(Script(connectionCloseFailure = SQLException("insert connection close"))) {
            var callbackCount = 0
            assertFailsWith<SQLException> {
                QueryExecute().executeInsert(BuiltQuery("INSERT INTO t VALUES (1)", mutableListOf())) {
                    callbackCount++
                    assertIs<ExecuteResult.Success<Long>>(it)
                }
            }
            assertEquals(1, callbackCount)
        }
    }

    @Test
    fun statementAndConnectionCloseFailuresPropagateAfterSingleCallback() {
        withDatabase(Script(statementCloseFailure = SQLException("statement close"))) { statementFailure ->
            var statementCallbacks = 0
            assertFailsWith<SQLException> {
                QueryExecute().executeUpdate(BuiltQuery("UPDATE t SET x = 1", mutableListOf())) {
                    statementCallbacks++
                    assertIs<ExecuteResult.Success<Int>>(it)
                }
            }
            assertEquals(1, statementCallbacks)
            assertEquals(1, statementFailure.connectionCloseCount)
        }

        val connectionFailure = Script(connectionCloseFailure = SQLException("connection close"))
        withDatabase(connectionFailure) {
            var callbackCount = 0
            assertFailsWith<SQLException> {
                QueryExecute().executeUpdate(BuiltQuery("UPDATE t SET x = 1", mutableListOf())) {
                    callbackCount++
                    assertIs<ExecuteResult.Success<Int>>(it)
                }
            }
            assertEquals(1, callbackCount)
        }
    }

    @Test
    fun independentBuiltAndBoundExecutionsCloseTheirOwnedConnectionExactlyOnce() {
        val builtScript = Script()
        withDatabase(builtScript) {
            var callbackCount = 0
            QueryExecute().executeUpdate(BuiltQuery("UPDATE t SET value = 1", mutableListOf())) { result ->
                callbackCount++
                assertIs<ExecuteResult.Success<Int>>(result)
            }

            assertEquals(1, callbackCount)
            assertEquals(1, builtScript.connectionAttempts)
            assertEquals(1, builtScript.connectionCloseCount)
            assertEquals(1, builtScript.operationCount)
            assertEquals(1, builtScript.statementCloseCount)
        }

        val boundScript = Script()
        withDatabase(boundScript) {
            var callbackCount = 0
            QueryExecute().executeUpdate(BoundQuery("UPDATE t SET value = 1", emptyList())) { result ->
                callbackCount++
                assertIs<ExecuteResult.Success<Int>>(result)
            }

            assertEquals(1, callbackCount)
            assertEquals(1, boundScript.connectionAttempts)
            assertEquals(1, boundScript.connectionCloseCount)
            assertEquals(1, boundScript.operationCount)
            assertEquals(1, boundScript.statementCloseCount)
        }
    }

    @Test
    fun ddlIgnoresStatementExecuteBooleanAndReportsSuccessTrue() {
        for (executeResult in listOf(true, false)) {
            withDatabase(Script(executeResult = executeResult)) { _ ->
                var callbackCount = 0
                var result: ExecuteResult<Boolean>? = null
                QueryExecute().executeTable(BuiltQuery("DDL", mutableListOf())) {
                    callbackCount++
                    result = it
                }
                assertEquals(1, callbackCount)
                assertEquals(ExecuteResult.Success(true), result)
            }
        }

        withDatabase(Script(executeFailure = SQLException("execute"))) {
            assertFailureOnce { callback ->
                QueryExecute().executeTable(BuiltQuery("DDL", mutableListOf()), callback)
            }
        }
    }

    @Test
    fun suppliedConnectionSelectSupportsBuiltAndBoundQueries() {
        assertSuppliedConnectionExecutesBuiltAndBound(
            expectedSql = "SELECT ?, ?",
            expectedBindings = listOf(
                listOf(1, 11, java.sql.Types.INTEGER),
                listOf(2, 22L, java.sql.Types.BIGINT),
            ),
            builtQuery = BuiltQuery(
                "SELECT :first, :second",
                mutableListOf(SqlParameter.of("second", 22L), SqlParameter.of("first", 11)),
            ),
            boundQuery = BoundQuery(
                "SELECT ?, ?",
                listOf(
                    QueryParameterBinding("first", 11, java.sql.Types.INTEGER),
                    QueryParameterBinding("second", 22L, java.sql.Types.BIGINT),
                ),
            ),
            executeBuilt = { connection, query, callback -> QueryExecute().executeSelect(connection, query, callback) },
            executeBound = { connection, query, callback -> QueryExecute().executeSelect(connection, query, callback) },
        ) { _, result ->
            val rows = assertNotNull(assertIs<ExecuteResult.Success<ResultSet>>(result).result)
            assertTrue(!rows.isClosed)
            rows.close()
        }
    }

    @Test
    fun suppliedConnectionInsertSupportsBuiltAndBoundQueries() {
        assertSuppliedConnectionExecutesBuiltAndBound(
            expectedSql = "INSERT INTO t (value) VALUES (?)",
            expectedBindings = listOf(listOf(1, 31, java.sql.Types.INTEGER)),
            builtQuery = BuiltQuery(
                "INSERT INTO t (value) VALUES (:value)",
                mutableListOf(SqlParameter.of("value", 31)),
            ),
            boundQuery = BoundQuery(
                "INSERT INTO t (value) VALUES (?)",
                listOf(QueryParameterBinding("value", 31, java.sql.Types.INTEGER)),
            ),
            executeBuilt = { connection, query, callback -> QueryExecute().executeInsert(connection, query, callback) },
            executeBound = { connection, query, callback -> QueryExecute().executeInsert(connection, query, callback) },
        ) { script, result ->
            assertEquals(ExecuteResult.Success(7L), result)
            assertTrue(script.resultSets.single().closed, "generated keys must be closed before callback returns")
        }
    }

    @Test
    fun suppliedConnectionUpdateSupportsBuiltAndBoundQueries() {
        assertSuppliedConnectionExecutesBuiltAndBound(
            expectedSql = "UPDATE t SET value = ? WHERE id = ?",
            expectedBindings = listOf(
                listOf(1, 41, java.sql.Types.INTEGER),
                listOf(2, 42L, java.sql.Types.BIGINT),
            ),
            builtQuery = BuiltQuery(
                "UPDATE t SET value = :value WHERE id = :id",
                mutableListOf(SqlParameter.of("id", 42L), SqlParameter.of("value", 41)),
            ),
            boundQuery = BoundQuery(
                "UPDATE t SET value = ? WHERE id = ?",
                listOf(
                    QueryParameterBinding("value", 41, java.sql.Types.INTEGER),
                    QueryParameterBinding("id", 42L, java.sql.Types.BIGINT),
                ),
            ),
            executeBuilt = { connection, query, callback -> QueryExecute().executeUpdate(connection, query, callback) },
            executeBound = { connection, query, callback -> QueryExecute().executeUpdate(connection, query, callback) },
        ) { _, result -> assertEquals(ExecuteResult.Success(1), result) }
    }

    @Test
    fun suppliedConnectionDeleteSupportsBuiltAndBoundQueries() {
        assertSuppliedConnectionExecutesBuiltAndBound(
            expectedSql = "DELETE FROM t WHERE id = ?",
            expectedBindings = listOf(listOf(1, 51L, java.sql.Types.BIGINT)),
            builtQuery = BuiltQuery(
                "DELETE FROM t WHERE id = :id",
                mutableListOf(SqlParameter.of("id", 51L)),
            ),
            boundQuery = BoundQuery(
                "DELETE FROM t WHERE id = ?",
                listOf(QueryParameterBinding("id", 51L, java.sql.Types.BIGINT)),
            ),
            executeBuilt = { connection, query, callback -> QueryExecute().executeDelete(connection, query, callback) },
            executeBound = { connection, query, callback -> QueryExecute().executeDelete(connection, query, callback) },
        ) { _, result -> assertEquals(ExecuteResult.Success(1), result) }
    }

    @Test
    fun multipleOperationsReuseTheSameSuppliedConnectionAndPreservePerQueryBindingOrder() =
        withSuppliedConnection(Script()) { script, connection ->
            val executor = QueryExecute()
            var callbackCount = 0

            executor.executeUpdate(
                connection,
                BuiltQuery(
                    "UPDATE t SET value = :value WHERE id = :id",
                    mutableListOf(SqlParameter.of("id", 62L), SqlParameter.of("value", 61)),
                ),
            ) { result ->
                callbackCount++
                assertEquals(ExecuteResult.Success(1), result)
            }
            executor.executeDelete(
                connection,
                BoundQuery(
                    "DELETE FROM t WHERE id = ? AND tenant = ?",
                    listOf(
                        QueryParameterBinding("id", 62L, java.sql.Types.BIGINT),
                        QueryParameterBinding("tenant", 63, java.sql.Types.INTEGER),
                    ),
                ),
            ) { result ->
                callbackCount++
                assertEquals(ExecuteResult.Success(1), result)
            }

            assertEquals(2, callbackCount)
            assertEquals(
                listOf(
                    "UPDATE t SET value = ? WHERE id = ?",
                    "DELETE FROM t WHERE id = ? AND tenant = ?",
                ),
                script.preparedSqls,
            )
            assertEquals(
                listOf<List<Any?>>(
                    listOf(1, 61, java.sql.Types.INTEGER),
                    listOf(2, 62L, java.sql.Types.BIGINT),
                    listOf(1, 62L, java.sql.Types.BIGINT),
                    listOf(2, 63, java.sql.Types.INTEGER),
                ),
                script.bindings,
            )
            assertEquals(2, script.operationCount)
            assertEquals(2, script.statementCloseCount)
            assertEquals(2, script.preparedConnections.size)
            assertSame(connection, script.preparedConnections[0])
            assertSame(connection, script.preparedConnections[1])
        }

    @Test
    fun suppliedConnectionTableExecutionDoesNotCloseTheConnection() = withSuppliedConnection(Script()) { script, connection ->
        var callbackCount = 0
        var result: ExecuteResult<Boolean>? = null
        QueryExecute().executeTable(
            connection,
            BuiltQuery("CREATE TABLE sample (id INTEGER)", mutableListOf()),
        ) {
            callbackCount++
            result = it
        }

        assertEquals(1, callbackCount)
        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(0, script.connectionAttempts)
        assertEquals(0, script.connectionCloseCount)
        assertEquals(1, script.statementCloseCount)
        assertEquals(1, script.operationCount)
        assertSame(connection, script.preparedConnections.single())
    }

    @Test
    fun suppliedConnectionJdbcFailuresInvokeOneCallbackAndLeaveConnectionOpen() {
        val prepareFailure = SQLException("injected prepare failed")
        withSuppliedConnection(Script(prepareFailure = prepareFailure)) { script, connection ->
            var callbackCount = 0
            var result: ExecuteResult.Failure? = null
            QueryExecute().executeSelect(connection, BoundQuery("SELECT 1", emptyList())) {
                callbackCount++
                result = assertIs<ExecuteResult.Failure>(it)
            }
            assertEquals(1, callbackCount)
            assertSame(prepareFailure, result?.exception)
            assertEquals(0, script.statementCloseCount)
        }

        val bindFailure = SQLException("injected bind failed")
        withSuppliedConnection(Script(bindFailure = bindFailure)) { script, connection ->
            var callbackCount = 0
            var result: ExecuteResult.Failure? = null
            QueryExecute().executeUpdate(
                connection,
                BuiltQuery("UPDATE t SET value = :value", mutableListOf(SqlParameter.of("value", 1))),
            ) {
                callbackCount++
                result = assertIs<ExecuteResult.Failure>(it)
            }
            assertEquals(1, callbackCount)
            assertSame(bindFailure, result?.exception)
            assertEquals(1, script.statementCloseCount)
        }

        val executeFailure = SQLException("injected update failed")
        withSuppliedConnection(Script(updateFailure = executeFailure)) { script, connection ->
            var callbackCount = 0
            var result: ExecuteResult.Failure? = null
            QueryExecute().executeDelete(connection, BoundQuery("DELETE FROM t", emptyList())) {
                callbackCount++
                result = assertIs<ExecuteResult.Failure>(it)
            }
            assertEquals(1, callbackCount)
            assertSame(executeFailure, result?.exception)
            assertEquals(1, script.statementCloseCount)
        }
    }

    @Test
    fun suppliedConnectionCallbackExceptionStaysPrimaryAndCloseFailureIsSuppressed() =
        withSuppliedConnection(Script(statementCloseFailure = SQLException("statement close failed"))) { script, connection ->
            val callbackFailure = IllegalStateException("consumer failed")
            var callbackCount = 0

            val thrown = assertFailsWith<IllegalStateException> {
                QueryExecute().executeSelect(connection, BoundQuery("SELECT 1", emptyList())) {
                    callbackCount++
                    throw callbackFailure
                }
            }

            assertSame(callbackFailure, thrown)
            assertEquals(1, callbackCount)
            assertEquals(listOf("statement close failed"), thrown.suppressed.map { it.message })
            assertTrue(script.resultSets.single().closed)
            assertEquals(1, script.statementCloseCount)
        }

    @Test
    fun unsupportedSuppliedConnectionExecutionFailsInsteadOfFallingBackToAnOwnedConnection() {
        var regularSelectCalls = 0
        val legacyImplementation = object : IQueryExecute {
            override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
                regularSelectCalls++
            }

            override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) = Unit
            override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) = Unit
            override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) = Unit
            override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) = Unit
        }

        val suppliedConnection = connectionProxy(Script())
        var callbackCount = 0
        val result = assertIs<ExecuteResult.Failure>(
            run {
                var captured: ExecuteResult<ResultSet>? = null
                legacyImplementation.executeSelect(
                    suppliedConnection,
                    BuiltQuery("SELECT 1", mutableListOf()),
                ) {
                    callbackCount++
                    captured = it
                }
                captured
            },
        )

        assertEquals(1, callbackCount)
        assertEquals(0, regularSelectCalls)
        assertIs<UnsupportedOperationException>(result.exception)
    }

    private fun <T> assertFailureOnce(execute: ((ExecuteResult<T>) -> Unit) -> Unit) {
        var callbackCount = 0
        var failure: ExecuteResult.Failure? = null
        execute {
            callbackCount++
            failure = assertIs<ExecuteResult.Failure>(it)
        }
        assertEquals(1, callbackCount)
        assertNotNull(failure)
    }

    private fun <T> withDatabase(script: Script, block: (Script) -> T): T {
        val previous = DefaultDatabaseConfig.config
        val driver = RecordingDriver(script)
        DriverManager.registerDriver(driver)
        DefaultDatabaseConfig.config = DatabaseConfig(
            dbDomain = "jdbc:executor-contract",
            dbPort = 5555,
            dbName = "test",
            dbUserName = "test",
            dbPassword = "",
        )
        return try {
            block(script)
        } finally {
            DefaultDatabaseConfig.config = previous
            DriverManager.deregisterDriver(driver)
        }
    }

    private fun <T> assertSuppliedConnectionExecutesBuiltAndBound(
        expectedSql: String,
        expectedBindings: List<List<Any?>>,
        builtQuery: BuiltQuery,
        boundQuery: BoundQuery,
        executeBuilt: (Connection, BuiltQuery, (ExecuteResult<T>) -> Unit) -> Unit,
        executeBound: (Connection, BoundQuery, (ExecuteResult<T>) -> Unit) -> Unit,
        verifyResult: (Script, ExecuteResult<T>) -> Unit,
    ) {
        val executions = listOf<(Connection, (ExecuteResult<T>) -> Unit) -> Unit>(
            { connection, callback -> executeBuilt(connection, builtQuery, callback) },
            { connection, callback -> executeBound(connection, boundQuery, callback) },
        )

        executions.forEach { execute ->
            val script = Script()
            withSuppliedConnection(script) { currentScript, connection ->
                var callbackCount = 0
                execute(connection) { result ->
                    callbackCount++
                    verifyResult(currentScript, result)
                }

                assertEquals(1, callbackCount)
                assertEquals(expectedSql, currentScript.preparedSql)
                assertEquals(expectedBindings, currentScript.bindings)
                assertEquals(1, currentScript.operationCount)
                assertEquals(1, currentScript.statementCloseCount)
                assertSame(connection, currentScript.preparedConnections.single())
                assertTrue(currentScript.resultSets.all { it.closed })
            }
        }
    }

    private fun <T> withSuppliedConnection(script: Script, block: (Script, Connection) -> T): T =
        withDatabase(script) {
            val connection = connectionProxy(script)
            val result = block(script, connection)
            assertEquals(0, script.connectionAttempts, "injected execution must not acquire a connection")
            assertEquals(0, script.connectionCloseCount, "injected execution must not close the supplied connection")
            result
        }

    private class Script(
        var connectionFailure: SQLException? = null,
        var prepareFailure: SQLException? = null,
        var bindFailure: SQLException? = null,
        var queryFailure: SQLException? = null,
        var updateFailure: SQLException? = null,
        var executeFailure: SQLException? = null,
        var generatedKeysFailure: SQLException? = null,
        var generatedKeysCloseFailure: SQLException? = null,
        var resultSetCloseFailure: SQLException? = null,
        var statementCloseFailure: SQLException? = null,
        var connectionCloseFailure: SQLException? = null,
        var updateCount: Int = 1,
        var generatedKey: Long? = 7L,
        var executeResult: Boolean = false,
    ) {
        var connectionAttempts = 0
        var connectionCloseCount = 0
        var prepareCount = 0
        var statementCloseCount = 0
        var operationCount = 0
        var preparedSql: String? = null
        val preparedSqls = mutableListOf<String>()
        val preparedConnections = mutableListOf<Connection>()
        val bindings = mutableListOf<List<Any?>>()
        val resultSets = mutableListOf<ResultSetState>()
    }

    private inner class RecordingDriver(private val script: Script) : Driver {
        override fun connect(url: String?, info: Properties?): Connection? {
            if (url == null || !acceptsURL(url)) return null
            script.connectionAttempts++
            script.connectionFailure?.let { throw it }
            return connectionProxy(script)
        }

        override fun acceptsURL(url: String?): Boolean = url?.startsWith("jdbc:executor-contract:") == true
        override fun getPropertyInfo(url: String?, info: Properties?) = emptyArray<java.sql.DriverPropertyInfo>()
        override fun getMajorVersion() = 1
        override fun getMinorVersion() = 0
        override fun jdbcCompliant() = false
        override fun getParentLogger(): Logger = Logger.getLogger("executor-contract-test")
    }

    private class ResultSetState(
        val proxy: ResultSet,
        var closed: Boolean = false,
    )

    private fun connectionProxy(script: Script): Connection = proxy(Connection::class.java) { connection, method, args ->
        when (method.name) {
            "prepareStatement" -> {
                script.prepareCount++
                val sql = args?.get(0) as String
                script.preparedSql = sql
                script.preparedSqls += sql
                script.preparedConnections += connection as Connection
                script.prepareFailure?.let { throw it }
                preparedStatementProxy(script)
            }
            "close" -> {
                script.connectionCloseCount++
                script.connectionCloseFailure?.let { throw it }
                null
            }
            "isClosed" -> script.connectionCloseCount > 0
            "isValid" -> true
            "toString" -> "RecordingConnection"
            else -> defaultValue(method.returnType)
        }
    }

    private fun preparedStatementProxy(script: Script): PreparedStatement = proxy(PreparedStatement::class.java) { _, method, args ->
        when (method.name) {
            "setObject", "setNull", "setInt", "setLong", "setString", "setBoolean", "setDouble", "setFloat", "setDate", "setTime", "setTimestamp" -> {
                script.bindings += args.orEmpty().toList()
                script.bindFailure?.let { throw it }
                null
            }
            "executeQuery" -> {
                script.operationCount++
                script.queryFailure?.let { throw it }
                resultSetProxy(
                    script,
                    hasRow = false,
                    generatedKey = null,
                    closeFailure = script.resultSetCloseFailure,
                )
            }
            "executeUpdate" -> {
                script.operationCount++
                script.updateFailure?.let { throw it }
                script.updateCount
            }
            "getGeneratedKeys" -> {
                script.generatedKeysFailure?.let { throw it }
                resultSetProxy(script, hasRow = script.generatedKey != null, generatedKey = script.generatedKey, closeFailure = script.generatedKeysCloseFailure)
            }
            "execute" -> {
                script.operationCount++
                script.executeFailure?.let { throw it }
                script.executeResult
            }
            "close" -> {
                script.statementCloseCount++
                script.resultSets.forEach { it.closed = true }
                script.statementCloseFailure?.let { throw it }
                null
            }
            "isClosed" -> false
            "toString" -> "RecordingPreparedStatement"
            else -> defaultValue(method.returnType)
        }
    }

    private fun resultSetProxy(
        script: Script,
        hasRow: Boolean,
        generatedKey: Long?,
        closeFailure: SQLException? = null,
    ): ResultSet {
        var read = false
        lateinit var state: ResultSetState
        val resultSet = proxy(ResultSet::class.java) { _, method, _ ->
            when (method.name) {
                "next" -> if (!read && hasRow) { read = true; true } else false
                "getLong" -> generatedKey ?: 0L
                "close" -> {
                    state.closed = true
                    closeFailure?.let { throw it }
                    null
                }
                "isClosed" -> state.closed
                "toString" -> "RecordingResultSet"
                else -> defaultValue(method.returnType)
            }
        }
        state = ResultSetState(resultSet)
        script.resultSets += state
        return state.proxy
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(
        type: Class<T>,
        handler: InvocationHandler,
    ): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T

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
