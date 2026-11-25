package gog.my_project.data_base_migration.facade

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionSelection
import gog.my_project.data_base_migration.core.tags.MigrationVersionTag
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
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
import kotlin.test.assertTrue

class MigrationFacadeTest {
    private val migrationVersionTag = object : MigrationVersionTag {
        override val value: String = "v1"
    }

    @Test
    fun `state keeps legacy JVM methods and adds typed selection overloads`() {
        val stateMethods = MigrationFacade::class.java.declaredMethods.filter { it.name == "state" }

        assertTrue(stateMethods.any { it.parameterCount == 0 })
        assertTrue(stateMethods.any { it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType)) })
        assertTrue(stateMethods.any {
            it.parameterTypes.contentEquals(arrayOf(MigrationVersionSelection::class.java))
        })
        assertTrue(stateMethods.any { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) })
    }

    @Test
    fun `recovery exposes the Boolean continuation option`() {
        val recoveryMethods = MigrationFacade::class.java.declaredMethods.filter { it.name == "recovery" }

        assertTrue(recoveryMethods.any {
            it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
        })
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
                recovery(),
                status(),
                current(),
            )
        }

        assertEquals(8, results.size)
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

        val firstInvocation = managed(firstJdbc) { scope ->
            migrationFacade(configuration("MainVersion"), scope) { status() }
        }
        val secondInvocation = managed(secondJdbc) { scope ->
            migrationFacade(configuration("AnalyticsVersion"), scope) { status() }
        }
        val first = requireNotNull(firstInvocation.value)
        val second = requireNotNull(secondInvocation.value)

        val firstReport = assertIs<ExecuteResult.Success<MigrationStatusReport>>(first).result
        val secondReport = assertIs<ExecuteResult.Success<MigrationStatusReport>>(second).result
        assertEquals(listOf("MainVersion"), firstReport?.versions?.map { it.name })
        assertEquals(listOf("AnalyticsVersion"), secondReport?.versions?.map { it.name })
        assertEquals(2, firstJdbc.queryCount)
        assertEquals(2, secondJdbc.queryCount)
    }

    @Test
    fun `current reads through the exact supplied scope and returns null for empty history`() {
        val jdbc = EmptyJdbc()

        val invocation = managed(jdbc) { scope -> migrationFacade(configuration("v1"), scope) { current() } }
        val result = requireNotNull(invocation.value)

        assertIs<ExecuteResult.Success<MigrationVersion?>>(result)
        assertEquals(null, result.result)
        assertEquals(3, jdbc.queryCount)
        assertEquals(null, invocation.scope.firstFailure)
    }

    @Test
    fun `commands execute immediately in order and preserve the block return value`() {
        val jdbc = EmptyJdbc()
        val blockResult = Any()

        val invocation = managed(jdbc) { scope ->
            migrationFacade(configuration("v1"), scope) {
                status()
                assertEquals(2, jdbc.queryCount, "status must finish before the next block statement")

                current()
                assertEquals(5, jdbc.queryCount, "current must finish before the block returns")
                blockResult
            }
        }

        assertSame(blockResult, invocation.value)
        assertEquals(5, jdbc.queryCount)
        assertEquals(null, invocation.scope.firstFailure)
        assertEquals(2, jdbc.lockAcquireCount)
        assertEquals(2, jdbc.lockReleaseCount)
    }

    @Test
    fun `reader failure from supplied connection is propagated unchanged`() {
        val jdbcFailure = SQLException("test-only reader failure")
        val jdbc = EmptyJdbc(executeFailure = jdbcFailure)

        val invocation = managed(jdbc) { scope -> migrationFacade(configuration("v1"), scope) { status() } }
        val result = requireNotNull(invocation.value)

        assertSame(jdbcFailure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(jdbcFailure, invocation.scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `fatal errors retain Operation fatal state and escape the facade`() {
        val fatal = AssertionError("test-only fatal")
        val jdbc = EmptyJdbc(executeFailure = fatal)
        var scope: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            managedExecutor(jdbc.connection).connection { managedScope ->
                scope = managedScope
                migrationFacade(configuration("v1"), managedScope) { status() }
                ExecuteResult.Success(Unit)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, requireNotNull(scope).fatalError)
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

    private fun <T> managed(
        jdbc: EmptyJdbc,
        block: (OperationExecutionScope) -> T,
    ): ManagedInvocation<T> {
        var scope: OperationExecutionScope? = null
        var value: T? = null
        val operationResult = managedExecutor(jdbc.connection).connection { managedScope ->
            scope = managedScope
            value = block(managedScope)
            ExecuteResult.Success(Unit)
        }
        return ManagedInvocation(operationResult, requireNotNull(scope), value)
    }

    private fun managedExecutor(connection: Connection): OperationExecutionExecutor {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://migration-facade-test.local",
                dbPort = 3306,
                dbName = "test_db",
                dbUserName = "test-user",
                dbPassword = "test-password",
            ),
            platform = DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
        )
        return OperationExecutionExecutor(
            DatabaseContext(
                name = "migration-facade-test",
                config = config,
                connectionProvider = object : IDatabaseConnection {
                    override fun build(): Connection = connection
                },
                databaseIdentity = requireNotNull(DatabaseIdentity.from(config)),
            ),
        )
    }

    private data class ManagedInvocation<T>(
        val operationResult: ExecuteResult<Unit>,
        val scope: OperationExecutionScope,
        val value: T?,
    )

    private inner class EmptyJdbc(
        private val executeFailure: Throwable? = null,
        private val ddlFailure: Throwable? = null,
    ) {
        var queryCount: Int = 0
            private set
        var lockAcquireCount: Int = 0
            private set
        var lockReleaseCount: Int = 0
            private set
        var ddlCount: Int = 0
            private set

        val connection: Connection = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(Connection::class.java),
        ) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> statement(args!![0] as String)
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        } as Connection

        private fun statement(sql: String): PreparedStatement = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "executeQuery" -> {
                    when {
                        "get_lock(" in sql.lowercase() -> {
                            lockAcquireCount++
                            resultSet(mapOf("acquired" to 1))
                        }
                        "release_lock(" in sql.lowercase() -> {
                            lockReleaseCount++
                            resultSet(mapOf("released" to 1))
                        }
                        else -> {
                            queryCount++
                            executeFailure?.let { throw it }
                            if (sql.contains("information_schema.COLUMNS", ignoreCase = true)) {
                                retryCheckpointColumnProbeResultSet()
                            } else {
                                resultSet()
                            }
                        }
                    }
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

        private fun resultSet(row: Map<String, Any?>? = null): ResultSet {
            var rowReturned = false
            return Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "next" -> if (row == null || rowReturned) false else {
                        rowReturned = true
                        true
                    }
                    "getInt" -> (row?.get(args?.firstOrNull()?.toString()) as? Number)?.toInt() ?: 0
                    "getMetaData" -> metadata(row?.keys?.toList().orEmpty())
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }

        private fun retryCheckpointColumnProbeResultSet(): ResultSet {
            var returnedRow = false
            return Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ResultSet::class.java),
            ) { _, method, args ->
                when (method.name) {
                    "next" -> if (returnedRow) false else {
                        returnedRow = true
                        true
                    }
                    "getString" -> when (args?.firstOrNull()) {
                        "selected_schema" -> "test_db"
                        else -> error("Unexpected retry_checkpoint probe column: ${args?.firstOrNull()}")
                    }
                    "getLong" -> when (args?.firstOrNull()) {
                        "column_count" -> 1L
                        else -> error("Unexpected retry_checkpoint probe column: ${args?.firstOrNull()}")
                    }
                    "wasNull" -> false
                    "getMetaData" -> metadata(listOf("selected_schema", "column_count"))
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            } as ResultSet
        }

        private fun metadata(
            columnLabels: List<String> = emptyList(),
        ): ResultSetMetaData = Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ResultSetMetaData::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getColumnCount" -> columnLabels.size
                "getColumnLabel", "getColumnName" -> columnLabels[(args!![0] as Int) - 1]
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
