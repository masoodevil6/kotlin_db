package gog.my_project.data_base_migration.executor.controller

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationTagTypeCodes
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport
import model.migration_status.MigrationVersionStatus
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.ArrayDeque
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationControllerTest {
    private val configuration = MigrationConfiguration(
        listOf(
            MigrationVersion("v1", emptyList()),
            MigrationVersion("v2", emptyList()),
            MigrationVersion("v3", emptyList()),
        ),
    )

    @Test
    fun `init returns successful initializer result`() {
        val events = mutableListOf<String>()
        val scope = testScope(connection(events))
        val controller = MigrationController(configuration)

        val result = controller.init(scope)

        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    @Test
    fun `init returns initializer failure with the original cause`() {
        val events = mutableListOf<String>()
        val originalFailure = SQLException("system table initialization failed")
        val scope = testScope(connection(events, failure = originalFailure))
        val controller = MigrationController(configuration)

        val result = controller.init(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(originalFailure, scope.firstFailure)
        assertEquals(0, events.count { it == "autoCommit:false" })
        assertEquals(0, events.count { it == "commit" })
        assertEquals(0, events.count { it == "rollback" })
        assertEquals(0, events.count { it == "connection-close" })
    }

    @Test
    fun `current returns the latest applied public Version`() {
        val jdbc = historyConnection(history(appliedVersionCount = 2))
        val scope = testScope(jdbc.connection)

        val result = MigrationController(configuration).current(scope)

        val current = assertIs<ExecuteResult.Success<MigrationVersion?>>(result).result
        assertSame(configuration.registrations[1], current)
        assertEquals("v2", current?.name)
        assertEquals(3, jdbc.queryCount)
    }

    @Test
    fun `current returns success null when no Version has been applied`() {
        val jdbc = historyConnection(history(appliedVersionCount = 0))
        val scope = testScope(jdbc.connection)

        val result = MigrationController(configuration).current(scope)

        assertNull(assertIs<ExecuteResult.Success<MigrationVersion?>>(result).result)
        assertEquals(3, jdbc.queryCount)
    }

    @Test
    fun `current propagates invalid History failure`() {
        val invalid = history(appliedVersionCount = 1).copy(
            versions = listOf(mapOf("id" to 100, "tagId" to 999)),
        )
        val jdbc = historyConnection(invalid)
        val scope = testScope(jdbc.connection)

        val result = MigrationController(configuration).current(scope)

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("references missing Tag"))
        assertSame(failure.exception, scope.firstFailure)
        assertEquals(3, jdbc.queryCount)
    }

    @Test
    fun `current preserves original History reader failure`() {
        val originalFailure = SQLException("history read failed")
        val jdbc = historyConnection(history(appliedVersionCount = 0), queryFailure = originalFailure)
        val scope = testScope(jdbc.connection)

        val result = MigrationController(configuration).current(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(originalFailure, scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `current rejects an invalid Operation scope before reading History`() {
        val jdbc = historyConnection(history(appliedVersionCount = 0))
        val scope = testScope(jdbc.connection)
        val originalFailure = IllegalStateException("scope already failed")
        scope.failed<Any?>(originalFailure)

        val result = MigrationController(configuration).current(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, jdbc.queryCount)
    }

    @Test
    fun `status returns report for the controller configured Versions`() {
        val jdbc = StatusJdbc(emptyList())
        val scope = testScope(jdbc.connection)

        val result = MigrationController(configuration).status(scope)

        val report = assertIs<MigrationStatusReport>(assertIs<ExecuteResult.Success<MigrationStatusReport>>(result).result)
        assertEquals(listOf("v1", "v2", "v3"), report.versions.map { it.name })
        assertEquals(
            listOf(MigrationVersionStatus.PENDING, MigrationVersionStatus.PENDING, MigrationVersionStatus.PENDING),
            report.versions.map { it.status },
        )
        assertEquals(1, jdbc.queryCount)
        assertEquals(0, jdbc.transactionControlCalls)
        assertEquals(0, jdbc.connectionCloseCount)
    }

    @Test
    fun `status propagates reader failure without replacing its cause`() {
        val originalFailure = SQLException("status query failed")
        val jdbc = StatusJdbc(emptyList(), queryFailure = originalFailure)
        val scope = testScope(jdbc.connection)

        val result = MigrationController(configuration).status(scope)

        assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(originalFailure, scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    private fun testScope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))

    private fun connection(
        events: MutableList<String>,
        failure: SQLException? = null,
    ): Connection = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(Connection::class.java),
    ) { _, method, args ->
        when (method.name) {
            "setAutoCommit" -> {
                events += "autoCommit:${args?.get(0)}"
                null
            }
            "prepareStatement" -> Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(PreparedStatement::class.java),
            ) { _, statementMethod, _ ->
                when (statementMethod.name) {
                    "execute" -> {
                        if (failure != null) throw failure
                        true
                    }
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(statementMethod.returnType)
                }
            } as PreparedStatement
            "commit" -> {
                events += "commit"
                null
            }
            "rollback" -> {
                events += "rollback"
                null
            }
            "close" -> {
                events += "connection-close"
                null
            }
            "isClosed" -> false
            else -> defaultValue(method.returnType)
        }
    } as Connection

    private fun history(appliedVersionCount: Int): HistoryRows {
        val versions = listOf(
            Triple("v1", 10, 100),
            Triple("v2", 20, 200),
            Triple("v3", 30, 300),
        )
        val tags = versions.map { (name, tagId, _) ->
            mapOf(
                "id" to tagId,
                "tagName" to name,
                "tagType" to MigrationTagTypeCodes.VERSION,
            )
        }
        val persistedVersions = versions.take(appliedVersionCount).map { (_, tagId, versionId) ->
            mapOf("id" to versionId, "tagId" to tagId)
        }
        return HistoryRows(tags, persistedVersions, emptyList())
    }

    private fun historyConnection(
        rows: HistoryRows,
        queryFailure: Throwable? = null,
    ): HistoryJdbc = HistoryJdbc(rows, queryFailure)

    private data class HistoryRows(
        val tags: List<Map<String, Any?>>,
        val versions: List<Map<String, Any?>>,
        val applied: List<Map<String, Any?>>,
    )

    private inner class HistoryJdbc(
        rows: HistoryRows,
        private val queryFailure: Throwable? = null,
    ) {
        private val responses = ArrayDeque(listOf(rows.tags, rows.versions, rows.applied))
        var queryCount = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> statement()
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun statement(): PreparedStatement = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "executeQuery" -> {
                    queryCount++
                    queryFailure?.let { throw it }
                    resultSet(responses.removeFirst())
                }
                "close" -> null
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        private fun resultSet(rows: List<Map<String, Any?>>): ResultSet {
            var index = -1
            var wasNull = false
            return Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "next" -> ++index < rows.size
                    "getInt", "getString" -> {
                        val value = rows[index][args?.firstOrNull()?.toString().orEmpty()]
                        wasNull = value == null
                        if (method.name == "getInt") (value as? Number)?.toInt() ?: 0 else value?.toString()
                    }
                    "wasNull" -> wasNull
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }
    }

    private inner class StatusJdbc(
        private val rows: List<Map<String, Any?>>,
        private val queryFailure: Throwable? = null,
    ) {
        var queryCount = 0
            private set
        var transactionControlCalls = 0
            private set
        var connectionCloseCount = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "prepareStatement" -> Proxy.newProxyInstance(
                    javaClass.classLoader,
                    arrayOf(PreparedStatement::class.java),
                ) { _, statementMethod, _ ->
                    when (statementMethod.name) {
                        "executeQuery" -> {
                            queryCount++
                            queryFailure?.let { throw it }
                            resultSet(rows)
                        }
                        "close" -> null
                        "isClosed" -> false
                        else -> defaultValue(statementMethod.returnType)
                    }
                } as PreparedStatement
                "commit", "rollback", "setAutoCommit" -> {
                    transactionControlCalls++
                    null
                }
                "close" -> {
                    connectionCloseCount++
                    null
                }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun resultSet(values: List<Map<String, Any?>>): ResultSet {
            var index = -1
            var wasNull = false
            return Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "next" -> ++index < values.size
                    "getInt", "getString" -> {
                        val value = values[index][args?.firstOrNull()?.toString().orEmpty()]
                        wasNull = value == null
                        if (method.name == "getInt") (value as? Number)?.toInt() ?: 0 else value?.toString()
                    }
                    "wasNull" -> wasNull
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
