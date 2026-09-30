package gog.my_project.data_base.migration.executor.internal.system_migration

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.executor.manager.MigrationExecutor
import gog.my_project.data_base.migration.api.interfaces.MigrationIdentity
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.Types
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SystemMigrationStateStoreTest {
    private lateinit var previousConfig: gog.my_project.data_base.core.data_base.DatabaseConfig

    @BeforeTest
    fun configureDialect() {
        previousConfig = DefaultDatabaseConfig.config
        DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)
    }

    @AfterTest
    fun restoreConfig() {
        DefaultDatabaseConfig.config = previousConfig
    }

    @Test
    fun initializeBootstrapsIdempotentlyThenReadsEmptyHistory() {
        val manager = RecordingQueryExecute(resultSet(emptyList()))
        val store = store(manager)
        var actual: ExecuteResult<SystemMigrationState>? = null

        store.initialize { actual = it }

        assertEquals(1, manager.tableQueries.size)
        assertTrue(manager.tableQueries.single().query.orEmpty().contains("CREATE TABLE IF NOT EXISTS `system_migration`"))
        assertTrue(manager.tableQueries.single().query.orEmpty().contains("`migration` VARCHAR(255) NOT NULL"))
        assertTrue(manager.tableQueries.single().query.orEmpty().contains("UNIQUE"))
        assertEquals(
            "select id , migration , batch from system_migration order by id asc",
            normalizeSql(manager.selectQueries.single().query),
        )
        assertEquals(SystemMigrationState(emptyList(), null), (actual as ExecuteResult.Success).result)
    }

    @Test
    fun readReturnsOrderedHistoryAndMaximumBatch() {
        val rows = listOf(
            Row(1, "20260101000000_create_users", 1),
            Row(2, "20260102000000_add_email", 1),
            Row(3, "20260103000000_add_index", 3),
        )
        val manager = RecordingQueryExecute(resultSet(rows))
        var actual: ExecuteResult<SystemMigrationState>? = null

        store(manager).read { actual = it }

        assertEquals(SystemMigrationState(rows.map { AppliedMigration(it.id, it.migration, it.batch) }, 3),
            (actual as ExecuteResult.Success).result)
    }

    @Test
    fun readRejectsCorruptRowsAndClosesResultSet() {
        val rows = listOf(Row(1, "valid", 1), Row(2, "invalid", 0))
        val resultSet = resultSet(rows)
        val manager = RecordingQueryExecute(resultSet)
        var actual: ExecuteResult<SystemMigrationState>? = null

        store(manager).read { actual = it }

        assertIs<ExecuteResult.Failure>(actual)
        assertTrue(resultSet.isClosed)
    }

    @Test
    fun readRejectsDuplicateIdentity() {
        val manager = RecordingQueryExecute(resultSet(listOf(Row(1, "same", 1), Row(2, "same", 2))))
        var actual: ExecuteResult<SystemMigrationState>? = null

        store(manager).read { actual = it }

        assertIs<ExecuteResult.Failure>(actual)
    }

    @Test
    fun readPropagatesManagerFailure() {
        val failure = IllegalStateException("read failed")
        val manager = RecordingQueryExecute(resultSet(emptyList()), selectFailure = failure)
        var actual: ExecuteResult<SystemMigrationState>? = null

        store(manager).read { actual = it }

        assertSame(failure, (actual as ExecuteResult.Failure).exception)
    }

    @Test
    fun recordsSuccessfulMigrationUsingBoundParameters() {
        val manager = RecordingQueryExecute(resultSet(emptyList()), updateResult = ExecuteResult.Success(1))
        var actual: ExecuteResult<Unit>? = null

        store(manager).recordSuccessfulMigration("migration_1", 2) { actual = it }

        val query = manager.updateQueries.single()
        assertEquals(
            "insert into system_migration ( migration , batch ) values ( :migration , :batch )",
            normalizeSql(query.query),
        )
        assertEquals(listOf("migration", "batch"), query.params.map { it.name })
        assertEquals(listOf("migration_1", 2), query.params.map { it.value })
        assertEquals(listOf(Types.VARCHAR, Types.INTEGER), query.params.map { it.sqlType })
        assertTrue(manager.selectQueries.isEmpty())
        assertEquals(ExecuteResult.Success(Unit), actual)
    }

    @Test
    fun recordsTheExactMigrationIdentityValueWithoutRewritingIt() {
        val identity = MigrationIdentity.fromAnnotationValue("create_users2_email")
        val manager = RecordingQueryExecute(resultSet(emptyList()), updateResult = ExecuteResult.Success(1))
        var actual: ExecuteResult<Unit>? = null

        store(manager).recordSuccessfulMigration(identity.value, 1) { actual = it }

        assertEquals("create_users2_email", manager.updateQueries.single().params.first().value)
        assertEquals(ExecuteResult.Success(Unit), actual)
    }

    @Test
    fun recordPropagatesDatabaseFailure() {
        val failure = IllegalStateException("duplicate migration")
        val manager = RecordingQueryExecute(resultSet(emptyList()), updateResult = ExecuteResult.Failure(failure))
        var actual: ExecuteResult<Unit>? = null

        store(manager).recordSuccessfulMigration("migration_1", 1) { actual = it }

        assertSame(failure, (actual as ExecuteResult.Failure).exception)
    }

    @Test
    fun recordPropagatesUniqueConstraintFailureWithoutPreRead() {
        val duplicateFailure = IllegalStateException("duplicate migration identity")
        val manager = RecordingQueryExecute(
            resultSet(emptyList()),
            updateResult = ExecuteResult.Failure(duplicateFailure),
        )
        var actual: ExecuteResult<Unit>? = null

        store(manager).recordSuccessfulMigration("already_applied", 2) { actual = it }

        assertSame(duplicateFailure, (actual as ExecuteResult.Failure).exception)
        assertEquals(1, manager.updateQueries.size)
        assertTrue(manager.selectQueries.isEmpty())
    }

    @Test
    fun recordRejectsInvalidIdentityAndBatchWithoutDatabaseCall() {
        val manager = RecordingQueryExecute(resultSet(emptyList()))
        var blankIdentityResult: ExecuteResult<Unit>? = null
        var invalidBatchResult: ExecuteResult<Unit>? = null

        store(manager).recordSuccessfulMigration("  ", 1) { blankIdentityResult = it }
        store(manager).recordSuccessfulMigration("migration_1", 0) { invalidBatchResult = it }

        assertIs<ExecuteResult.Failure>(blankIdentityResult)
        assertIs<ExecuteResult.Failure>(invalidBatchResult)
        assertTrue(manager.updateQueries.isEmpty())
    }

    @Test
    fun bootstrapFailureDoesNotAttemptToReadHistory() {
        val failure = IllegalStateException("bootstrap failed")
        val manager = RecordingQueryExecute(resultSet(emptyList()), tableResult = ExecuteResult.Failure(failure))
        var actual: ExecuteResult<SystemMigrationState>? = null

        store(manager).initialize { actual = it }

        assertSame(failure, (actual as ExecuteResult.Failure).exception)
        assertTrue(manager.selectQueries.isEmpty())
    }

    private fun store(manager: RecordingQueryExecute): SystemMigrationStateStore {
        return SystemMigrationStateStore(manager, MigrationExecutor(queryExecutor = manager))
    }

    private data class Row(val id: Int, val migration: String, val batch: Int)

    private class RecordingQueryExecute(
        private val rows: ResultSet,
        private val tableResult: ExecuteResult<Boolean> = ExecuteResult.Success(true),
        private val updateResult: ExecuteResult<Int> = ExecuteResult.Success(1),
        private val selectFailure: Throwable? = null,
    ) : IQueryExecute {
        val tableQueries = mutableListOf<BuiltQuery>()
        val selectQueries = mutableListOf<BuiltQuery>()
        val updateQueries = mutableListOf<BuiltQuery>()

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            tableQueries += builtQuery
            blockExecute(tableResult)
        }

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
            selectQueries += builtQuery
            val failure = selectFailure
            if (failure == null) blockExecute(ExecuteResult.Success(rows))
            else blockExecute(ExecuteResult.Failure(failure))
        }

        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) {
            updateQueries += builtQuery
            blockExecute(updateResult)
        }

        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected executeInsert")

        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected executeDelete")
    }

    private fun resultSet(rows: List<Row>): ResultSet {
        var index = -1
        var closed = false
        return Proxy.newProxyInstance(
            ResultSet::class.java.classLoader,
            arrayOf(ResultSet::class.java),
        ) { _, method, args ->
            when (method.name) {
                "next" -> ++index < rows.size
                "getInt" -> when (args!![0]) {
                    "id" -> rows[index].id
                    "batch" -> rows[index].batch
                    else -> error("Unexpected column ${args[0]}")
                }
                "getString" -> when (args!![0]) {
                    "migration" -> rows[index].migration
                    else -> error("Unexpected column ${args[0]}")
                }
                "close" -> { closed = true; null }
                "isClosed" -> closed
                "toString" -> "FakeResultSet"
                else -> error("Unexpected ResultSet method ${method.name}")
            }
        } as ResultSet
    }

    private fun normalizeSql(query: String?): String =
        query.orEmpty().replace(Regex("\\s+"), " ").trim()
}
