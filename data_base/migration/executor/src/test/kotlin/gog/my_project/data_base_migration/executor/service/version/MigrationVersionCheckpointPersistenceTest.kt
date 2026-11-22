package gog.my_project.data_base_migration.executor.service.version

import gog.my_project.data_base_manager.execute.tools.ExecuteResult
import gog.my_project.data_base_migration.api.model.migration_state.MigrationVersionState
import gog.my_project.data_base_operation.config.data_base.DatabasePlatformConfig
import gog.my_project.data_base_operation.config.query.dialect.DialectQuery
import gog.my_project.data_base_operation.executor.transaction.OperationExecutionScope
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MigrationVersionCheckpointPersistenceTest {
    private val persistence = MigrationVersionCheckpointPersistence()

    private data class Call(val sql: String, val values: List<Any?>, val sqlTypes: List<Int>)

    @Test
    fun `UP Version insertion persists started status and checkpoint`() {
        val jdbc = JdbcScript()
        val result = persistence.insertUpVersion(scope(jdbc), tagId = 9, checkpoint = "checkpoint-json")

        assertEquals(17, assertIs<ExecuteResult.Success<Int>>(result).result)
        val call = jdbc.calls.single()
        assertEquals(
            "INSERT INTO system_migration_version (tag_id, status, message, retry_checkpoint) VALUES (?, ?, ?, ?)",
            call.sql,
        )
        assertEquals(listOf(9, MigrationVersionState.STATUS_MIGRATE_STARTED, null, "checkpoint-json"), call.values)
        assertEquals(listOf(Types.INTEGER, Types.INTEGER, Types.VARCHAR, Types.VARCHAR), call.sqlTypes)
    }

    @Test
    fun `checkpoint persistence operations use fixed conditional statements`() {
        val cases = listOf(
            Case(
                expectedSql = "UPDATE system_migration_version SET status = ?, retry_checkpoint = ? " +
                    "WHERE id = ? AND status = ? AND retry_checkpoint IS NULL",
                expectedValues = listOf(
                    MigrationVersionState.STATUS_ROLLBACK_STARTED,
                    "down-start",
                    31,
                    MigrationVersionState.STATUS_MIGRATE_COMPLETED,
                ),
                action = { persistence.startDown(scope(it), 31, "down-start") },
            ),
            Case(
                expectedSql = "UPDATE system_migration_version SET retry_checkpoint = ? " +
                    "WHERE id = ? AND status = ? AND CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
                expectedValues = listOf("next", 32, MigrationVersionState.STATUS_MIGRATE_STARTED, "expected"),
                action = { persistence.advanceCheckpoint(scope(it), 32, 100, "expected", "next") },
            ),
            Case(
                expectedSql = "UPDATE system_migration_version SET status = ? " +
                    "WHERE id = ? AND status = ? AND CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
                expectedValues = listOf(
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    33,
                    MigrationVersionState.STATUS_MIGRATE_FAILED_RECORD,
                    "expected-up-record",
                ),
                action = { persistence.normalizeUpRecordingFailure(scope(it), 33, "expected-up-record") },
            ),
            Case(
                expectedSql = "UPDATE system_migration_version SET status = ? " +
                    "WHERE id = ? AND status = ? AND CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
                expectedValues = listOf(
                    MigrationVersionState.STATUS_ROLLBACK_STARTED,
                    34,
                    MigrationVersionState.STATUS_ROLLBACK_FAILED_RECORD,
                    "expected-down-record",
                ),
                action = { persistence.normalizeDownRecordingFailure(scope(it), 34, "expected-down-record") },
            ),
            Case(
                expectedSql = "UPDATE system_migration_version SET status = ?, retry_checkpoint = NULL " +
                    "WHERE id = ? AND status = ? AND CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
                expectedValues = listOf(
                    MigrationVersionState.STATUS_MIGRATE_COMPLETED,
                    35,
                    MigrationVersionState.STATUS_MIGRATE_STARTED,
                    "expected-final-up",
                ),
                action = { persistence.finalizeUp(scope(it), 35, "expected-final-up") },
            ),
            Case(
                expectedSql = "DELETE FROM system_migration_version WHERE id = ? AND status = ? AND " +
                    "CAST(retry_checkpoint AS BINARY) = CAST(? AS BINARY)",
                expectedValues = listOf(36, MigrationVersionState.STATUS_ROLLBACK_STARTED, "expected-final-down"),
                action = { persistence.finalizeDown(scope(it), 36, "expected-final-down") },
            ),
        )

        cases.forEach { case ->
            val jdbc = JdbcScript()
            val result = assertIs<ExecuteResult.Success<MigrationCheckpointWriteResult>>(case.action(jdbc))
            assertEquals(MigrationCheckpointWriteResult.APPLIED, result.result)
            assertEquals(case.expectedSql, jdbc.calls.single().sql)
            assertEquals(case.expectedValues, jdbc.calls.single().values)
        }
    }

    @Test
    fun `zero one and multiple affected rows classify as not applied applied and indeterminate`() {
        listOf(
            0 to MigrationCheckpointWriteResult.NOT_APPLIED,
            1 to MigrationCheckpointWriteResult.APPLIED,
            2 to MigrationCheckpointWriteResult.INDETERMINATE,
        ).forEach { (affectedRows, expected) ->
            val updateJdbc = JdbcScript(affectedRows = affectedRows)
            val update = persistence.advanceCheckpoint(scope(updateJdbc), 41, 100, "old", "new")
            assertEquals(expected, assertIs<ExecuteResult.Success<MigrationCheckpointWriteResult>>(update).result)

            val deleteJdbc = JdbcScript(affectedRows = affectedRows)
            val delete = persistence.finalizeDown(scope(deleteJdbc), 42, "final")
            assertEquals(expected, assertIs<ExecuteResult.Success<MigrationCheckpointWriteResult>>(delete).result)
            assertEquals(1, updateJdbc.calls.size)
            assertEquals(1, deleteJdbc.calls.size)
        }
    }

    private data class Case(
        val expectedSql: String,
        val expectedValues: List<Any?>,
        val action: (JdbcScript) -> ExecuteResult<MigrationCheckpointWriteResult>,
    )

    private fun scope(jdbc: JdbcScript) = OperationExecutionScope(
        jdbc.connection,
        DatabasePlatformConfig(dialect = DialectQuery.MY_SQL),
    )

    private inner class JdbcScript(
        private val affectedRows: Int = 1,
        private val generatedKey: Long = 17,
    ) {
        val calls = mutableListOf<Call>()
        val connection: Connection = proxy(Connection::class.java) { method, args ->
            when (method.name) {
                "prepareStatement" -> preparedStatement(args?.get(0).toString())
                "isClosed" -> false
                else -> defaultValue(method.returnType)
            }
        }

        private fun preparedStatement(sql: String): PreparedStatement {
            val values = mutableMapOf<Int, Any?>()
            val sqlTypes = mutableMapOf<Int, Int>()
            return proxy(PreparedStatement::class.java) { method, args ->
                when (method.name) {
                    "setObject" -> {
                        val index = args!![0] as Int
                        values[index] = args[1]
                        sqlTypes[index] = args[2] as Int
                        null
                    }
                    "setNull" -> {
                        val index = args!![0] as Int
                        values[index] = null
                        sqlTypes[index] = args[1] as Int
                        null
                    }
                    "executeUpdate" -> {
                        calls += Call(
                            sql,
                            values.toSortedMap().values.toList(),
                            sqlTypes.toSortedMap().values.toList(),
                        )
                        affectedRows
                    }
                    "getGeneratedKeys" -> resultSet()
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            }
        }

        private fun resultSet(): ResultSet {
            var returned = false
            return proxy(ResultSet::class.java) { method, _ ->
                when (method.name) {
                    "next" -> if (!returned) { returned = true; true } else false
                    "getLong" -> generatedKey
                    "wasNull" -> false
                    "isClosed" -> false
                    else -> defaultValue(method.returnType)
                }
            }
        }
    }

    private fun <T> proxy(type: Class<T>, invoke: (java.lang.reflect.Method, Array<out Any?>?) -> Any?): T =
        type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
                when (method.name) {
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "Proxy(${type.simpleName})"
                    else -> invoke(method, args)
                }
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
