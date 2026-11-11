package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import gog.my_project.data_base_migration.executor.support.systemMigrationResultSetMetadata
import model.version_history.MigrationHistoryEntry
import model.version_history.MigrationHistoryTag
import model.version_history.MigrationHistoryVersion
import model.version_history.MigrationVersionHistorySnapshot
import java.lang.reflect.Method
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

class MigrationVersionHistoryReaderTest {
    private val reader = MigrationVersionHistoryReader()

    @Test
    fun `reads all three tables independently and retains orphan and broken references`() {
        val jdbc = FakeJdbc(
            tags = listOf(
                row("id" to 11, "tagName" to "version-without-row", "tagType" to 0),
                row("id" to 17, "tagName" to "unreferenced-item", "tagType" to 1),
                row("id" to 22, "tagName" to "referenced-item", "tagType" to 1),
                row("id" to 23, "tagName" to "version-with-row", "tagType" to 0),
            ),
            versions = listOf(
                row("id" to 31, "tagId" to 900),
                row("id" to 32, "tagId" to 23),
            ),
            migrations = listOf(
                row("id" to 81, "tagId" to 22, "versionId" to 902, "createdAt" to null),
                row("id" to 82, "tagId" to 901, "versionId" to 31, "createdAt" to null),
                row("id" to 83, "tagId" to 22, "versionId" to 32, "createdAt" to "2026-05-01T12:30:00Z"),
            ),
        )

        val snapshot = success(jdbc)

        assertEquals(
            listOf(
                MigrationHistoryTag(11, "version-without-row", 0),
                MigrationHistoryTag(17, "unreferenced-item", 1),
                MigrationHistoryTag(22, "referenced-item", 1),
                MigrationHistoryTag(23, "version-with-row", 0),
            ),
            snapshot.tags,
        )
        assertEquals(listOf(MigrationHistoryVersion(31, 900), MigrationHistoryVersion(32, 23)), snapshot.versions)
        assertEquals(
            listOf(
                MigrationHistoryEntry(81, 22, 902, null),
                MigrationHistoryEntry(82, 901, 31, null),
                MigrationHistoryEntry(83, 22, 32, "2026-05-01T12:30:00Z"),
            ),
            snapshot.migrations,
        )
        assertEquals(3, jdbc.executedSql.size)
        assertTrue(jdbc.executedSql[0].contains("system_migration_tag"))
        assertTrue(jdbc.executedSql[1].contains("system_migration_version"))
        assertTrue(jdbc.executedSql[2].contains("system_migration"))
        val migrationSql = jdbc.executedSql[2].lowercase()
        assertTrue("created_at" in migrationSql)
        assertTrue("createdat" in migrationSql)
        assertTrue("system_migration_query" !in migrationSql)
    }

    @Test
    fun `sorts by id and preserves duplicate history with null or equal timestamps`() {
        val jdbc = FakeJdbc(
            tags = listOf(
                row("id" to 29, "tagName" to "last", "tagType" to 1),
                row("id" to 3, "tagName" to "first", "tagType" to 1),
            ),
            versions = listOf(
                row("id" to 41, "tagId" to 29),
                row("id" to 8, "tagId" to 3),
            ),
            migrations = listOf(
                row("id" to 50, "tagId" to 29, "versionId" to 41, "createdAt" to "same"),
                row("id" to 12, "tagId" to 3, "versionId" to 8, "createdAt" to "same"),
                row("id" to 24, "tagId" to 3, "versionId" to 8, "createdAt" to null),
                row("id" to 24, "tagId" to 3, "versionId" to 8, "createdAt" to null),
            ),
        )

        val snapshot = success(jdbc)

        assertEquals(listOf(3, 29), snapshot.tags.map(MigrationHistoryTag::id))
        assertEquals(listOf(8, 41), snapshot.versions.map(MigrationHistoryVersion::id))
        assertEquals(listOf(12, 24, 24, 50), snapshot.migrations.map(MigrationHistoryEntry::id))
        assertEquals(listOf("same", null, null, "same"), snapshot.migrations.map(MigrationHistoryEntry::createdAt))
    }

    @Test
    fun `preserves sparse persisted ids and raw foreign key values`() {
        val jdbc = FakeJdbc(
            tags = listOf(row("id" to 2, "tagName" to "tag", "tagType" to 0)),
            versions = listOf(row("id" to 7, "tagId" to 2), row("id" to 35, "tagId" to 2)),
            migrations = listOf(
                row("id" to 5, "tagId" to 12, "versionId" to 7, "createdAt" to null),
                row("id" to 66, "tagId" to 13, "versionId" to 35, "createdAt" to null),
            ),
        )

        val snapshot = success(jdbc)

        assertEquals(listOf(7, 35), snapshot.versions.map(MigrationHistoryVersion::id))
        assertEquals(listOf(5, 66), snapshot.migrations.map(MigrationHistoryEntry::id))
        assertEquals(listOf(12, 13), snapshot.migrations.map(MigrationHistoryEntry::tagId))
    }

    @Test
    fun `empty tables produce an empty snapshot`() {
        val snapshot = success(FakeJdbc(emptyList(), emptyList(), emptyList()))

        assertTrue(snapshot.tags.isEmpty())
        assertTrue(snapshot.versions.isEmpty())
        assertTrue(snapshot.migrations.isEmpty())
    }

    @Test
    fun `query failure is returned with its original cause`() {
        val failure = SQLException("version table read failed")
        val jdbc = FakeJdbc(emptyList(), emptyList(), emptyList(), failTable = "version", failure = failure)
        val scope = scope(jdbc.connection)

        val result = reader.read(scope)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertSame(failure, scope.firstFailure)
        assertEquals(2, jdbc.executedSql.size)
    }

    @Test
    fun `fatal query error is recorded and rethrown`() {
        val fatal = AssertionError("fatal history read")
        val jdbc = FakeJdbc(emptyList(), emptyList(), emptyList(), failTable = "tag", failure = fatal)
        val scope = scope(jdbc.connection)

        val thrown = assertFailsWith<AssertionError> { reader.read(scope) }

        assertSame(fatal, thrown)
        assertSame(fatal, scope.fatalError)
    }

    @Test
    fun `reuses the supplied scope connection without owning its lifecycle`() {
        val jdbc = FakeJdbc(emptyList(), emptyList(), emptyList())
        val scope = scope(jdbc.connection)

        assertIs<ExecuteResult.Success<MigrationVersionHistorySnapshot>>(reader.read(scope))

        assertEquals(3, jdbc.prepareStatementCount)
        assertEquals(3, jdbc.statementCloseCount)
        assertEquals(3, jdbc.resultSetCloseCount)
        assertEquals(0, jdbc.connectionCloseCount)
        assertEquals(0, jdbc.transactionControlCount)
    }

    @Test
    fun `failed scope is rejected before any table is read`() {
        val jdbc = FakeJdbc(emptyList(), emptyList(), emptyList())
        val scope = scope(jdbc.connection)
        val failure = IllegalStateException("scope already failed")
        scope.failed<Any?>(failure)

        val result = reader.read(scope)

        assertSame(failure, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, jdbc.prepareStatementCount)
    }

    private fun success(jdbc: FakeJdbc): MigrationVersionHistorySnapshot {
        val result = reader.read(scope(jdbc.connection))
        return assertIs<MigrationVersionHistorySnapshot>(
            assertIs<ExecuteResult.Success<MigrationVersionHistorySnapshot>>(result).result,
        )
    }

    private fun scope(connection: Connection) =
        OperationExecutionScope(connection, DatabasePlatformConfig(dialect = DialectQuery.MY_SQL))

    private fun row(vararg values: Pair<String, Any?>): Map<String, Any?> = mapOf(*values)

    private inner class FakeJdbc(
        private val tags: List<Map<String, Any?>>,
        private val versions: List<Map<String, Any?>>,
        private val migrations: List<Map<String, Any?>>,
        private val failTable: String? = null,
        private val failure: Throwable? = null,
    ) {
        var prepareStatementCount = 0
            private set
        var statementCloseCount = 0
            private set
        var resultSetCloseCount = 0
            private set
        var connectionCloseCount = 0
            private set
        var transactionControlCount = 0
            private set
        val executedSql = mutableListOf<String>()

        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    prepareStatementCount++
                    val sql = args.firstOrNull()?.toString().orEmpty()
                    executedSql += sql
                    statement(sql)
                }
                "close" -> {
                    connectionCloseCount++
                    null
                }
                "commit", "rollback", "setAutoCommit" -> {
                    transactionControlCount++
                    null
                }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        }

        private fun statement(sql: String): PreparedStatement = proxy(PreparedStatement::class.java) { method, _ ->
            when (method.name) {
                "executeQuery" -> {
                    val table = tableFor(sql)
                    if (table == failTable) throw requireNotNull(failure)
                    val rows = when (table) {
                        "tag" -> tags
                        "version" -> versions
                        else -> migrations
                    }
                    resultSet(sql, rows)
                }
                "close" -> {
                    statementCloseCount++
                    null
                }
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        }

        private fun resultSet(sql: String, rows: List<Map<String, Any?>>): ResultSet {
            var rowIndex = -1
            var wasNull = false
            return proxy(ResultSet::class.java) { method, args ->
                when (method.name) {
                    "getMetaData" -> systemMigrationResultSetMetadata(sql)
                    "next" -> ++rowIndex < rows.size
                    "getInt", "getString" -> {
                        val value = rows[rowIndex][args.first().toString()]
                        wasNull = value == null
                        if (method.name == "getInt") (value as? Number)?.toInt() ?: 0 else value?.toString()
                    }
                    "wasNull" -> wasNull
                    "close" -> {
                        resultSetCloseCount++
                        null
                    }
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            }
        }

        private fun tableFor(sql: String): String {
            val lower = sql.lowercase()
            return when {
                "system_migration_tag" in lower -> "tag"
                "system_migration_version" in lower -> "version"
                else -> "migration"
            }
        }
    }

    private fun <T> proxy(type: Class<T>, handler: (Method, Array<out Any?>) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                handler(method, args ?: emptyArray())
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
