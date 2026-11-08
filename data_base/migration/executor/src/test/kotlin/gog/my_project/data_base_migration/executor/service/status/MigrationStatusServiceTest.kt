package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.registration.configuration.MigrationConfiguration
import gog.my_project.data_base_migration.api.registration.group.SingleMigration
import gog.my_project.data_base_migration.api.registration.item.RegisteredMigration
import gog.my_project.data_base_migration.api.registration.version.MigrationVersion
import gog.my_project.data_base_migration.core.migrate.Migration
import gog.my_project.data_base_migration.core.migrate.MigrationDefinition
import gog.my_project.data_base_migration.core.migrate.MigrationIdentity
import gog.my_project.data_base_migration.core.tags.MigrationItemTag
import gog.my_project.data_base_migration.core.tags.MigrationTagType
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class MigrationStatusServiceTest {
    private val configuration = MigrationConfiguration(
        listOf(
            MigrationVersion(
                "v1",
                listOf(
                    SingleMigration(
                        RegisteredMigration(
                            MigrationIdentity.fromValue("status_probe"),
                            CallbackProbeMigration::class,
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `reads and analyzes complete status through the supplied scope without running migrations`() {
        CallbackProbeMigration.callbackCount.set(0)
        val jdbc = FakeJdbc(appliedV1Rows())
        val scope = newScope(jdbc.connection)

        val result = MigrationStatusService(configuration).status(scope)

        val report = assertIs<MigrationStatusReport>(assertIs<ExecuteResult.Success<MigrationStatusReport>>(result).result)
        assertEquals(listOf("v1"), report.versions.map { it.name })
        assertEquals(listOf(MigrationVersionStatus.APPLIED), report.versions.map { it.status })
        assertEquals("status_probe", report.versions.single().migrations.single().id)
        assertEquals(1, jdbc.queryCount)
        assertEquals(0, jdbc.transactionControlCalls)
        assertEquals(0, jdbc.connectionCloseCount)
        assertEquals(0, CallbackProbeMigration.callbackCount.get())
    }

    @Test
    fun `empty history returns configured versions as pending`() {
        val jdbc = FakeJdbc(emptyList())
        val scope = newScope(jdbc.connection)

        val result = MigrationStatusService(configuration).status(scope)

        val report = assertIs<MigrationStatusReport>(assertIs<ExecuteResult.Success<MigrationStatusReport>>(result).result)
        assertEquals(listOf("v1"), report.versions.map { it.name })
        assertEquals(listOf(MigrationVersionStatus.PENDING), report.versions.map { it.status })
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `reader failure is propagated with its original cause`() {
        val original = SQLException("status history read failed")
        val jdbc = FakeJdbc(emptyList(), queryFailure = original)
        val scope = newScope(jdbc.connection)

        val result = MigrationStatusService(configuration).status(scope)

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(original, scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `rejected scope is returned without reading history`() {
        val jdbc = FakeJdbc(emptyList())
        val scope = newScope(jdbc.connection)
        val original = IllegalStateException("scope already failed")
        scope.failed<Any?>(original)

        val result = MigrationStatusService(configuration).status(scope)

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, jdbc.queryCount)
    }

    @Test
    fun `analyzer exception is recorded as the operation failure`() {
        val original = IllegalArgumentException("status analysis failed")
        val jdbc = FakeJdbc(emptyList())
        val scope = newScope(jdbc.connection)
        val analyzer = MigrationStatusAnalyzer { throw original }

        val result = MigrationStatusService(configuration, analyzer = analyzer).status(scope)

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(original, scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    @Test
    fun `fatal analyzer error is recorded and rethrown`() {
        val fatal = AssertionError("fatal status analysis error")
        val jdbc = FakeJdbc(emptyList())
        val scope = newScope(jdbc.connection)
        val analyzer = MigrationStatusAnalyzer { throw fatal }

        val thrown = assertFailsWith<AssertionError> {
            MigrationStatusService(configuration, analyzer = analyzer).status(scope)
        }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
        assertSame(fatal, scope.firstFailure)
        assertEquals(1, jdbc.queryCount)
    }

    private fun newScope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))

    private fun appliedV1Rows(): List<Map<String, Any?>> = listOf(
        mapOf(
            "tagId" to 1,
            "tagName" to "v1",
            "tagType" to MigrationTagType.VERSION.code,
            "tagVersionId" to 10,
            "migrationHistoryId" to null,
            "appliedVersionReferenceId" to null,
            "appliedVersionId" to null,
            "appliedVersionTagId" to null,
            "appliedVersionTagName" to null,
            "appliedVersionTagType" to null,
        ),
        mapOf(
            "tagId" to 2,
            "tagName" to "status_probe",
            "tagType" to MigrationTagType.ITEM.code,
            "tagVersionId" to null,
            "migrationHistoryId" to 20,
            "appliedVersionReferenceId" to 10,
            "appliedVersionId" to 10,
            "appliedVersionTagId" to 1,
            "appliedVersionTagName" to "v1",
            "appliedVersionTagType" to MigrationTagType.VERSION.code,
        ),
    )

    private inner class FakeJdbc(
        private val rows: List<Map<String, Any?>>,
        private val queryFailure: Throwable? = null,
    ) {
        var queryCount = 0
            private set
        var transactionControlCalls = 0
            private set
        var connectionCloseCount = 0
            private set

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> proxy(PreparedStatement::class.java) { statementMethod, _ ->
                    when (statementMethod.name) {
                        "executeQuery" -> {
                            queryCount++
                            queryFailure?.let { throw it }
                            resultSet()
                        }
                        "close" -> null
                        "isClosed" -> false
                        else -> defaultValue(statementMethod.returnType)
                    }
                }
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
        }

        private fun resultSet(): ResultSet {
            var index = -1
            var wasNull = false
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "next" -> ++index < rows.size
                    "getInt", "getString" -> {
                        val value = rows[index][args.first().toString()]
                        wasNull = value == null
                        if (method.name == "getInt") (value as? Number)?.toInt() ?: 0 else value?.toString()
                    }
                    "wasNull" -> wasNull
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            }
        }
    }

    private class CallbackProbeMigration : Migration(ProbeTag.STATUS_PROBE) {
        override fun up(): MigrationDefinition {
            callbackCount.incrementAndGet()
            error("Migration status must not execute up callbacks")
        }

        override fun down(): MigrationDefinition {
            callbackCount.incrementAndGet()
            error("Migration status must not execute down callbacks")
        }

        companion object {
            val callbackCount = AtomicInteger()
        }
    }

    private enum class ProbeTag(override val value: String) : MigrationItemTag {
        STATUS_PROBE("status_probe"),
    }

    private fun <T> proxy(
        type: Class<T>,
        invoke: (java.lang.reflect.Method, Array<out Any?>) -> Any?,
    ): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            invoke(method, args ?: emptyArray())
        },
    )

    private fun defaultValue(type: Class<*>): Any? = when (type) {
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
}
