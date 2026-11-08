package gog.my_project.data_base_migration.executor.service.status

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import model.migration_status.MigrationStatusRow
import model.migration_status.MigrationStatusSnapshot
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MigrationStatusReaderTest {
    private val reader = MigrationStatusReader()

    @Test
    fun `reads every status field and preserves nullable relationships`() {
        val row = statusRow(
            tagId = 7,
            tagName = "release-1",
            tagType = 0,
            tagVersionId = null,
            migrationHistoryId = 91,
            appliedVersionReferenceId = 800,
            appliedVersionId = null,
            appliedVersionTagId = null,
            appliedVersionTagName = null,
            appliedVersionTagType = null,
        )
        val invocation = invoke(listOf(row))

        val snapshot = assertIs<ExecuteResult.Success<MigrationStatusSnapshot>>(invocation.result).result
        assertEquals(
            toModel(row),
            assertIs<MigrationStatusSnapshot>(snapshot).rows.single(),
        )
        assertEquals(1, invocation.jdbc.queryCount)
        val sql = invocation.jdbc.sql.single().lowercase()
        assertTrue(sql.contains("system_migration_tag_query"), sql)
        assertTrue(sql.contains("system_migration_version_query"), sql)
        assertTrue(sql.contains("system_migration_query"), sql)
    }

    @Test
    fun `preserves result order and duplicate rows`() {
        val first = statusRow(tagId = 1, tagName = "orphan")
        val second = statusRow(tagId = 2, tagName = "applied", migrationHistoryId = 10)
        val invocation = invoke(listOf(first, second, first))

        val rows = assertIs<MigrationStatusSnapshot>(
            assertIs<ExecuteResult.Success<MigrationStatusSnapshot>>(invocation.result).result,
        ).rows
        assertEquals(listOf(1, 2, 1), rows.map(MigrationStatusRow::tagId))
        assertEquals(listOf("orphan", "applied", "orphan"), rows.map(MigrationStatusRow::tagName))
        assertEquals(listOf(first, second, first).map(::toModel), rows)
    }

    @Test
    fun `empty result produces an empty immutable snapshot`() {
        val invocation = invoke(emptyList())

        val snapshot = assertIs<MigrationStatusSnapshot>(
            assertIs<ExecuteResult.Success<MigrationStatusSnapshot>>(invocation.result).result,
        )
        assertTrue(snapshot.rows.isEmpty())
    }

    @Test
    fun `snapshot defensively copies and prevents row list mutation`() {
        val first = toModel(statusRow(tagId = 1))
        val second = toModel(statusRow(tagId = 2))
        val source = mutableListOf(first)

        val snapshot = MigrationStatusSnapshot(source)
        source += second

        assertEquals(listOf(first), snapshot.rows)
        assertFailsWith<UnsupportedOperationException> { (snapshot.rows as MutableList).add(second) }
    }

    @Test
    fun `query failure is propagated without replacing its cause`() {
        val original = SQLException("status query failed")
        val invocation = invoke(emptyList(), queryFailure = original)

        assertSame(original, assertIs<ExecuteResult.Failure>(invocation.result).exception)
        assertSame(original, invocation.scope.firstFailure)
        assertEquals(1, invocation.jdbc.queryCount)
    }

    @Test
    fun `invalid scope is rejected before executing the query`() {
        val jdbc = FakeJdbc(emptyList())
        val scope = newScope(jdbc.connection)
        val original = IllegalStateException("scope is already failed")
        scope.failed<Any?>(original)

        val result = reader.read(scope)

        assertSame(original, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, jdbc.queryCount)
    }

    @Test
    fun `fatal error is recorded and rethrown`() {
        val fatal = AssertionError("fatal status read")
        val jdbc = FakeJdbc(emptyList(), queryFailure = fatal)
        val scope = newScope(jdbc.connection)

        val thrown = assertFailsWith<AssertionError> { reader.read(scope) }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
    }

    @Test
    fun `reader uses the supplied scope without owning connection or transaction lifecycle`() {
        val invocation = invoke(emptyList())

        assertIs<ExecuteResult.Success<MigrationStatusSnapshot>>(invocation.result)
        assertEquals(0, invocation.jdbc.connectionCloseCount)
        assertEquals(0, invocation.jdbc.transactionControlCalls)
    }

    private fun invoke(rows: List<Map<String, Any?>>, queryFailure: Throwable? = null): Invocation {
        val jdbc = FakeJdbc(rows, queryFailure)
        val scope = newScope(jdbc.connection)
        return Invocation(reader.read(scope), scope, jdbc)
    }

    private fun newScope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))

    private fun statusRow(
        tagId: Int,
        tagName: String = "tag-$tagId",
        tagType: Int = 0,
        tagVersionId: Int? = null,
        migrationHistoryId: Int? = null,
        appliedVersionReferenceId: Int? = null,
        appliedVersionId: Int? = null,
        appliedVersionTagId: Int? = null,
        appliedVersionTagName: String? = null,
        appliedVersionTagType: Int? = null,
    ): Map<String, Any?> = mapOf(
        "tagId" to tagId,
        "tagName" to tagName,
        "tagType" to tagType,
        "tagVersionId" to tagVersionId,
        "migrationHistoryId" to migrationHistoryId,
        "appliedVersionReferenceId" to appliedVersionReferenceId,
        "appliedVersionId" to appliedVersionId,
        "appliedVersionTagId" to appliedVersionTagId,
        "appliedVersionTagName" to appliedVersionTagName,
        "appliedVersionTagType" to appliedVersionTagType,
    )

    private fun toModel(values: Map<String, Any?>) = MigrationStatusRow(
        tagId = values.getValue("tagId") as Int,
        tagName = values.getValue("tagName") as String,
        tagType = values.getValue("tagType") as Int,
        tagVersionId = values["tagVersionId"] as Int?,
        migrationHistoryId = values["migrationHistoryId"] as Int?,
        appliedVersionReferenceId = values["appliedVersionReferenceId"] as Int?,
        appliedVersionId = values["appliedVersionId"] as Int?,
        appliedVersionTagId = values["appliedVersionTagId"] as Int?,
        appliedVersionTagName = values["appliedVersionTagName"] as String?,
        appliedVersionTagType = values["appliedVersionTagType"] as Int?,
    )

    private data class Invocation(
        val result: ExecuteResult<MigrationStatusSnapshot>,
        val scope: OperationExecutionScope,
        val jdbc: FakeJdbc,
    )

    private inner class FakeJdbc(
        private val rows: List<Map<String, Any?>>,
        private val queryFailure: Throwable? = null,
    ) {
        var queryCount: Int = 0
            private set
        var connectionCloseCount: Int = 0
            private set
        var transactionControlCalls: Int = 0
            private set
        val sql: MutableList<String> = mutableListOf()

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
                else -> defaultValue(method.returnType)
            }
        }

        private fun statement(statementSql: String): PreparedStatement {
            sql += statementSql
            return proxy(PreparedStatement::class.java) { method, _ ->
                when (method.name) {
                    "executeQuery" -> {
                        queryCount++
                        queryFailure?.let { throw it }
                        resultSet()
                    }
                    "close" -> null
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
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
