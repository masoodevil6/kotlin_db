package gog.my_project.data_base.manager.execute.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
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
        var preparedSql: String? = null
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

    private fun connectionProxy(script: Script): Connection = proxy(Connection::class.java) { _, method, args ->
        when (method.name) {
            "prepareStatement" -> {
                script.prepareCount++
                script.preparedSql = args?.get(0) as String
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
                script.queryFailure?.let { throw it }
                resultSetProxy(
                    script,
                    hasRow = false,
                    generatedKey = null,
                    closeFailure = script.resultSetCloseFailure,
                )
            }
            "executeUpdate" -> {
                script.updateFailure?.let { throw it }
                script.updateCount
            }
            "getGeneratedKeys" -> {
                script.generatedKeysFailure?.let { throw it }
                resultSetProxy(script, hasRow = script.generatedKey != null, generatedKey = script.generatedKey, closeFailure = script.generatedKeysCloseFailure)
            }
            "execute" -> {
                script.executeFailure?.let { throw it }
                script.executeResult
            }
            "close" -> {
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
