package gog.my_project.data_base_migration.executor.service.version_selection

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationTagType
import gog.my_project.data_base_migration.core.tags.MigrationTagTypeCodes
import gog.my_project.data_base_migration.executor.service.version.MigrationVersionHistoryState
import gog.my_project.data_base_manager.connection.interfaces.IDatabaseConnection
import gog.my_project.data_base_manager.connection.manager.DatabaseContext
import gog.my_project.data_base_operation.config.data_base.DatabaseConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseConnectionConfig
import gog.my_project.data_base_operation.config.data_base.DatabaseIdentity
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base.dialect.DatabaseDialect
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionExecutor
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.prepared.PreparedMigrationVersion
import model.retry.MigrationRetryCheckpoint
import model.retry.MigrationRetryCheckpointCodec
import model.retry.MigrationRetryPhase
import model.version_history.MigrationHistoryVersion
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import java.util.ArrayDeque
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationCurrentVersionServiceTest {
    private val configuration = MigrationConfiguration(
        listOf(
            version("v1", "m1", FirstMigration::class.java),
            version("v2", "m2", SecondMigration::class.java),
            version("v3", "m3", ThirdMigration::class.java),
        ),
    )
    private val service = MigrationCurrentVersionService(configuration)

    @BeforeTest
    fun resetMigrationProbe() {
        MigrationProbe.constructorCalls = 0
        MigrationProbe.upCalls = 0
        MigrationProbe.downCalls = 0
    }

    @Test
    fun emptyHistoryReturnsSuccessNull() {
        val invocation = invoke(history(appliedVersionCount = 0))

        val success = assertIs<ExecuteResult.Success<PreparedMigrationVersion?>>(invocation.result)
        assertNull(success.result)
        assertEquals(3, invocation.jdbc.queryCount)
    }

    @Test
    fun oneAppliedVersionReturnsItsPreparedIdentityAndMigration() {
        val invocation = invoke(history(appliedVersionCount = 1))

        val current = assertNotNull(assertIs<ExecuteResult.Success<PreparedMigrationVersion?>>(invocation.result).result)
        assertEquals("v1", current.version.name)
        assertEquals(listOf("m1"), current.migrations.map { it.id.value })
    }

    @Test
    fun multipleAppliedVersionsReturnLatestInConfigurationOrder() {
        val invocation = invoke(history(appliedVersionCount = 2))

        val current = assertNotNull(assertIs<ExecuteResult.Success<PreparedMigrationVersion?>>(invocation.result).result)
        assertEquals("v2", current.version.name)
        assertEquals(listOf("m2"), current.migrations.map { it.id.value })
    }

    @Test
    fun validatedHistoryStateRetainsSnapshotAndCurrentVersionFromOneRead() {
        val jdbc = HistoryJdbc(history(appliedVersionCount = 2))
        val scope = newScope(jdbc.connection)

        val state = assertNotNull(
            assertIs<ExecuteResult.Success<MigrationVersionHistoryState>>(
                service.validatedHistoryState(scope),
            ).result,
        )

        assertEquals(listOf("v1", "v2"), state.appliedVersions.map { it.version.name })
        assertEquals("v2", state.currentVersion?.version?.name)
        assertEquals(listOf(100, 200), state.snapshot.versions.map { it.id })
        assertEquals(listOf(1000, 1001), state.snapshot.migrations.map { it.id })
        assertEquals(3, jdbc.queryCount)
        assertEquals(0, jdbc.dmlCount)
    }

    @Test
    fun invalidHistoryReturnsLatchedFailure() {
        val valid = history(appliedVersionCount = 1)
        val invalid = valid.copy(
            applied = valid.applied.map { it + ("versionId" to 999) },
        )
        val invocation = invoke(invalid)

        val failure = assertIs<ExecuteResult.Failure>(invocation.result)
        assertTrue(failure.exception.message.orEmpty().contains("references missing Version"))
        assertSame(failure.exception, invocation.scope.firstFailure)
    }

    @Test
    fun incompleteVersionStatusIsRejectedInsteadOfBecomingCurrent() {
        val valid = history(appliedVersionCount = 1)
        val incomplete = valid.copy(
            versions = valid.versions.map {
                it + mapOf(
                    "status" to MigrationVersionState.STATUS_MIGRATE_STARTED,
                    "retryCheckpoint" to MigrationRetryCheckpointCodec.encode(
                        MigrationRetryCheckpoint(
                            formatVersion = MigrationRetryCheckpointCodec.FORMAT_VERSION,
                            revision = 0,
                            originalStatus = MigrationVersionState.STATUS_MIGRATE_STARTED,
                            originalMessage = null,
                            cursorMigrationId = "migration_one",
                            phase = MigrationRetryPhase.EXECUTION_PENDING,
                        ),
                    ),
                )
            },
        )
        val invocation = invoke(incomplete)

        val failure = assertIs<ExecuteResult.Failure>(invocation.result)
        assertTrue(
            failure.exception.message.orEmpty().contains(
                "non-completed status ${MigrationVersionState.STATUS_MIGRATE_STARTED}",
            ),
        )
        assertSame(failure.exception, invocation.scope.firstFailure)
        assertEquals(3, invocation.jdbc.queryCount)
    }

    @Test
    fun historyReadFailureIsPropagatedWithoutReplacingItsCause() {
        val original = SQLException("history query failed")
        val invocation = invoke(history(appliedVersionCount = 0), queryFailure = original)

        val failure = assertIs<ExecuteResult.Failure>(invocation.result)
        assertSame(original, failure.exception)
        assertSame(original, invocation.scope.firstFailure)
        assertEquals(1, invocation.jdbc.queryCount)
    }

    @Test
    fun currentVersionUsesNoTransactionAndDoesNotModifyHistory() {
        val invocation = invoke(history(appliedVersionCount = 2))

        assertIs<ExecuteResult.Success<PreparedMigrationVersion?>>(invocation.result)
        assertEquals(3, invocation.jdbc.queryCount)
        assertEquals(1, invocation.jdbc.lockAcquireCount)
        assertEquals(1, invocation.jdbc.lockReleaseCount)
        assertEquals(0, invocation.jdbc.dmlCount)
        assertEquals(0, invocation.jdbc.transactionControlCalls)
        assertEquals(1, invocation.jdbc.connectionCloseCount)
        assertTrue(invocation.jdbc.sql.all { sql ->
            sql.contains("SELECT", ignoreCase = true) &&
                !Regex("\\b(INSERT|UPDATE|DELETE|CREATE|ALTER|DROP)\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql)
        })
        assertEquals(0, MigrationProbe.constructorCalls)
        assertEquals(0, MigrationProbe.upCalls)
        assertEquals(0, MigrationProbe.downCalls)
    }

    @Test
    fun failedScopeIsRejectedBeforeReadingHistory() {
        val jdbc = HistoryJdbc(history(appliedVersionCount = 0))
        val scope = newScope(jdbc.connection)
        val original = IllegalStateException("scope already failed")
        scope.failed<Any?>(original)

        val result = service.currentVersion(scope)

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, jdbc.queryCount)
    }

    @Test
    fun fatalReaderErrorIsRecordedAndRethrown() {
        val fatal = AssertionError("fatal history reader error")
        val jdbc = HistoryJdbc(history(appliedVersionCount = 0), queryFailure = fatal)
        var scope: OperationExecutionScope? = null

        val thrown = assertFailsWith<AssertionError> {
            managedExecutor(jdbc.connection).connection { managedScope ->
                scope = managedScope
                service.currentVersion(managedScope)
            }
        }

        assertSame(fatal, thrown)
        assertSame(fatal, requireNotNull(scope).fatalError)
    }

    private fun invoke(
        history: HistoryRows,
        queryFailure: Throwable? = null,
    ): Invocation {
        val jdbc = HistoryJdbc(history, queryFailure)
        var scope: OperationExecutionScope? = null
        val result = managedExecutor(jdbc.connection).connection { managedScope ->
            scope = managedScope
            service.currentVersion(managedScope)
        }
        return Invocation(result, requireNotNull(scope), jdbc)
    }

    private fun managedExecutor(connection: Connection): OperationExecutionExecutor {
        val config = DatabaseConfig(
            connection = DatabaseConnectionConfig(
                dbDomain = "jdbc:mysql://migration-current-test.local",
                dbPort = 3306,
                dbName = "migration_current_test",
                dbUserName = "test-user",
                dbPassword = "test-password",
            ),
            platform = DatabasePlatformConfig(dialect = DatabaseDialect.MYSQL),
        )
        val context = DatabaseContext(
            name = "migration-current-test",
            config = config,
            connectionProvider = object : IDatabaseConnection {
                override fun build(): Connection = connection
            },
            databaseIdentity = requireNotNull(DatabaseIdentity.from(config)),
        )
        return OperationExecutionExecutor(context)
    }

    private fun newScope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DatabaseDialect.MYSQL))

    private fun history(appliedVersionCount: Int): HistoryRows {
        val versionRows = listOf(
            VersionRow("v1", 10, 100, "m1", 11),
            VersionRow("v2", 20, 200, "m2", 21),
            VersionRow("v3", 30, 300, "m3", 31),
        )
        val tags = buildList {
            versionRows.forEach { row ->
                add(
                    mapOf(
                        "id" to row.tagId,
                        "tagName" to row.name,
                        "tagType" to MigrationTagTypeCodes.VERSION,
                    ),
                )
            }
            versionRows.take(appliedVersionCount).forEach { row ->
                add(
                    mapOf(
                        "id" to row.itemTagId,
                        "tagName" to row.migrationId,
                        "tagType" to MigrationTagType.ITEM.code,
                    ),
                )
            }
        }
        val versions = versionRows.take(appliedVersionCount).map { row ->
            mapOf(
                "id" to row.versionId,
                "tagId" to row.tagId,
                "status" to MigrationHistoryVersion.STATUS_MIGRATE_COMPLETED,
            )
        }
        val applied = versionRows.take(appliedVersionCount).mapIndexed { index, row ->
            mapOf("id" to 1000 + index, "tagId" to row.itemTagId, "versionId" to row.versionId)
        }
        return HistoryRows(tags, versions, applied)
    }

    private fun version(
        name: String,
        migrationId: String,
        migrationClass: Class<out Migration>,
    ) = MigrationVersion(
        name,
        listOf(
            SingleMigration(
                RegisteredMigration(
                    MigrationIdentity.fromValue(migrationId),
                    migrationClass.kotlin,
                ),
            ),
        ),
    )

    private data class VersionRow(
        val name: String,
        val tagId: Int,
        val versionId: Int,
        val migrationId: String,
        val itemTagId: Int,
    )

    private data class HistoryRows(
        val tags: List<Map<String, Any?>>,
        val versions: List<Map<String, Any?>>,
        val applied: List<Map<String, Any?>>,
    )

    private data class Invocation(
        val result: ExecuteResult<PreparedMigrationVersion?>,
        val scope: OperationExecutionScope,
        val jdbc: HistoryJdbc,
    )

    private inner class HistoryJdbc(
        rows: HistoryRows,
        private val queryFailure: Throwable? = null,
    ) {
        private val responses = ArrayDeque(listOf(rows.tags, rows.versions, rows.applied))
        val sql = mutableListOf<String>()
        var queryCount = 0
            private set
        var lockAcquireCount = 0
            private set
        var lockReleaseCount = 0
            private set
        var dmlCount = 0
            private set
        var transactionControlCalls = 0
            private set
        var connectionCloseCount = 0
            private set

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> statement(args.firstOrNull()?.toString().orEmpty())
                "commit", "rollback", "setAutoCommit" -> {
                    transactionControlCalls++
                    null
                }
                "close" -> {
                    connectionCloseCount++
                    null
                }
                "isClosed" -> false
                else -> primitiveDefault(method.returnType)
            }
        }

        private fun statement(statementSql: String): PreparedStatement {
            sql += statementSql
            var lockRows: ResultSet? = null
            return proxy(PreparedStatement::class.java) { method, _ ->
                when (method.name) {
                    "execute" -> {
                        if ("get_lock(" in statementSql.lowercase()) {
                            lockAcquireCount++
                            lockRows = resultSet(listOf(mapOf("acquired" to 1)), statementSql)
                        } else {
                            lockReleaseCount++
                            lockRows = resultSet(listOf(mapOf("released" to 1)), statementSql)
                        }
                        true
                    }
                    "getResultSet" -> lockRows
                    "getMoreResults" -> false
                    "getUpdateCount" -> -1
                    "executeQuery" -> {
                        when {
                            "get_lock(" in statementSql.lowercase() -> {
                                lockAcquireCount++
                                resultSet(listOf(mapOf("acquired" to 1)), statementSql)
                            }
                            "release_lock(" in statementSql.lowercase() -> {
                                lockReleaseCount++
                                resultSet(listOf(mapOf("released" to 1)), statementSql)
                            }
                            else -> {
                                queryCount++
                                queryFailure?.let { throw it }
                                resultSet(responses.removeFirst(), statementSql)
                            }
                        }
                    }
                    "executeUpdate" -> {
                        dmlCount++
                        0
                    }
                    "close" -> null
                    "isClosed" -> false
                    else -> primitiveDefault(method.returnType)
                }
            }
        }

        private fun resultSet(rows: List<Map<String, Any?>>, statementSql: String): ResultSet {
            var index = -1
            var wasNull = false
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "getMetaData" -> gog.my_project.data_base_migration.executor.support
                        .systemMigrationResultSetMetadata(statementSql)
                    "next" -> ++index < rows.size
                    "getObject" -> rows[index].values.firstOrNull()
                    "getInt", "getString" -> {
                        val value = rows[index][args.first().toString()]
                        wasNull = value == null
                        if (method.name == "getInt") (value as? Number)?.toInt() ?: 0 else value?.toString()
                    }
                    "wasNull" -> wasNull
                    "close" -> null
                    "isClosed" -> false
                    else -> primitiveDefault(method.returnType)
                }
            }
        }
    }

    private fun <T> proxy(
        type: Class<T>,
        invoke: (java.lang.reflect.Method, Array<out Any?>) -> Any?,
    ): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            invoke(method, args ?: emptyArray())
        },
    )

    private fun primitiveDefault(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Double.TYPE -> 0.0
        java.lang.Float.TYPE -> 0f
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private abstract class ProbeMigration(item: MigrationItemTag) : Migration(item) {
        init {
            MigrationProbe.constructorCalls++
        }

        override fun up(): MigrationDefinition {
            MigrationProbe.upCalls++
            error("Current-version lookup must not execute migration callbacks")
        }

        override fun down(): MigrationDefinition {
            MigrationProbe.downCalls++
            error("Current-version lookup must not execute migration callbacks")
        }
    }

    private class FirstMigration : ProbeMigration(Items.M1)
    private class SecondMigration : ProbeMigration(Items.M2)
    private class ThirdMigration : ProbeMigration(Items.M3)

    private enum class Items(override val value: String) : MigrationItemTag {
        M1("m1"),
        M2("m2"),
        M3("m3"),
    }

    private object MigrationProbe {
        var constructorCalls = 0
        var upCalls = 0
        var downCalls = 0
    }
}
