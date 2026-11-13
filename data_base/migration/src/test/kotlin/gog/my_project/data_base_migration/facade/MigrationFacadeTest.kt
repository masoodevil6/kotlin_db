package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusReport
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MigrationFacadeTest {
    private val migrationVersionTag = object : MigrationVersionTag {
        override val value: String = "v1"
    }

    @Test
    fun `all commands preserve failures from the supplied scope`() {
        val configuration = configuration("v1")
        val scope = scope(emptyConnection())
        val originalFailure = IllegalStateException("scope already failed")
        scope.failed<Any?>(originalFailure)

        val results = migrationFacade(configuration, scope) {
            listOf(
                init(),
                migrate(),
                migrate(migrationVersionTag),
                rollback(),
                rollback(migrationVersionTag),
                status(),
                current(),
            )
        }

        assertEquals(7, results.size)
        results.forEach { result ->
            assertSame(originalFailure, assertIs<ExecuteResult.Failure>(result).exception)
        }
    }

    @Test
    fun `init delegates through the supplied scope and returns successful result`() {
        val jdbc = EmptyJdbc()
        val scope = scope(jdbc.connection)

        val result = migrationFacade(configuration("v1"), scope) { init() }

        assertIs<ExecuteResult.Success<Boolean>>(result)
        assertEquals(true, result.result)
        assertEquals(3, jdbc.ddlCount)
        assertEquals(null, scope.firstFailure)
    }

    @Test
    fun `init propagates DDL failure and records it in the supplied scope`() {
        val failure = SQLException("test-only initialization failure")
        val jdbc = EmptyJdbc(ddlFailure = failure)
        val scope = scope(jdbc.connection)

        val result = migrationFacade(configuration("v1"), scope) { init() }

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(failure, scope.firstFailure)
        assertEquals(1, jdbc.ddlCount)
    }

    @Test
    fun `init preserves fatal Error behavior`() {
        val fatal = AssertionError("test-only initialization fatal")
        val jdbc = EmptyJdbc(ddlFailure = fatal)
        val scope = scope(jdbc.connection)

        val thrown = assertFailsWith<AssertionError> {
            migrationFacade(configuration("v1"), scope) { init() }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertEquals(1, jdbc.ddlCount)
    }

    @Test
    fun `status uses each facade's explicit configuration independently`() {
        val firstJdbc = EmptyJdbc()
        val secondJdbc = EmptyJdbc()

        val first = migrationFacade(configuration("MainVersion"), scope(firstJdbc.connection)) { status() }
        val second = migrationFacade(configuration("AnalyticsVersion"), scope(secondJdbc.connection)) { status() }

        val firstReport = assertIs<ExecuteResult.Success<MigrationStatusReport>>(first).result
        val secondReport = assertIs<ExecuteResult.Success<MigrationStatusReport>>(second).result
        assertEquals(listOf("MainVersion"), firstReport?.versions?.map { it.name })
        assertEquals(listOf("AnalyticsVersion"), secondReport?.versions?.map { it.name })
        assertEquals(1, firstJdbc.queryCount)
        assertEquals(1, secondJdbc.queryCount)
    }

    @Test
    fun `current reads through the exact supplied scope and returns null for empty history`() {
        val jdbc = EmptyJdbc()
        val scope = scope(jdbc.connection)

        val result = migrationFacade(configuration("v1"), scope) { current() }

        assertIs<ExecuteResult.Success<MigrationVersion?>>(result)
        assertEquals(null, result.result)
        assertEquals(3, jdbc.queryCount)
        assertEquals(null, scope.firstFailure)
    }

    @Test
    fun `commands execute immediately in order and preserve the block return value`() {
        val jdbc = EmptyJdbc()
        val scope = scope(jdbc.connection)
        val blockResult = Any()

        val result = migrationFacade(configuration("v1"), scope) {
            status()
            assertEquals(1, jdbc.queryCount, "status must finish before the next block statement")

            current()
            assertEquals(4, jdbc.queryCount, "current must finish before the block returns")
            blockResult
        }

        assertSame(blockResult, result)
        assertEquals(4, jdbc.queryCount)
        assertEquals(null, scope.firstFailure)
    }

    @Test
    fun `reader failure from supplied connection is propagated unchanged`() {
        val jdbcFailure = SQLException("test-only reader failure")
        val jdbc = EmptyJdbc(executeFailure = jdbcFailure)
        val scope = scope(jdbc.connection)

        val result = migrationFacade(configuration("v1"), scope) { status() }

        assertSame(jdbcFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(jdbcFailure, scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `fatal errors retain Operation fatal state and escape the facade`() {
        val fatal = AssertionError("test-only fatal")
        val jdbc = EmptyJdbc(executeFailure = fatal)
        val scope = scope(jdbc.connection)

        val thrown = assertFailsWith<AssertionError> {
            migrationFacade(configuration("v1"), scope) { status() }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `block exceptions are not swallowed`() {
        val scope = scope(emptyConnection())
        val originalFailure = IllegalArgumentException("block failure")

        val thrown = assertFailsWith<IllegalArgumentException> {
            migrationFacade(configuration("v1"), scope) { throw originalFailure }
        }

        assertSame(originalFailure, thrown)
    }

    private fun configuration(vararg versions: String) = MigrationConfiguration(
        versions.map { MigrationVersion(it, emptyList()) },
    )

    private fun scope(connection: Connection) = OperationExecutionScope(
        connection,
        DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
    )

    private fun emptyConnection() = EmptyJdbc().connection

    private inner class EmptyJdbc(
        private val executeFailure: Throwable? = null,
        private val ddlFailure: Throwable? = null,
    ) {
        var queryCount: Int = 0
            private set
        var ddlCount: Int = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, _ ->
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
                    executeFailure?.let { throw it }
                    resultSet()
                }
                "execute" -> {
                    ddlCount++
                    ddlFailure?.let { throw it }
                    true
                }
                "close" -> null
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as PreparedStatement

        private fun resultSet(): ResultSet = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "next" -> false
                "getMetaData" -> metadata()
                "close" -> null
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as ResultSet

        private fun metadata(): ResultSetMetaData = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ResultSetMetaData::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getColumnCount" -> 0
                else -> defaultValue(method.returnType)
            }
        } as ResultSetMetaData
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
