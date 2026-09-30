package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.builder.createIndex
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorCreateIndexTest {
    @Test
    fun delegatesSqlAndReturnsManagerSuccess() {
        val manager = RecordingQueryExecute(ExecuteResult.Success(true))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createIndex {
                    tableName("users")
                    name("idx_users_email")
                    column("email")
                },
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        assertEquals("CREATE INDEX `idx_users_email` ON `users` (`email`)", manager.executedQuery?.query)
        assertEquals(ExecuteResult.Success(true), received)
    }

    @Test
    fun delegatesSqlAndPreservesManagerFailure() {
        val failure = IllegalStateException("duplicate index")
        val manager = RecordingQueryExecute(ExecuteResult.Failure(failure))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null
        var reportedQuery: String? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createIndex {
                    tableName("users")
                    name("idx_users_email")
                    column("email")
                },
                blockExecute = { received = it },
                blockQueryInfo = { query, _ -> reportedQuery = query },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        val expected = "CREATE INDEX `idx_users_email` ON `users` (`email`)"
        assertEquals(expected, manager.executedQuery?.query)
        assertEquals(StringTools.formatSql(expected), reportedQuery)
        assertSame(failure, (received as ExecuteResult.Failure).exception)
    }

    @Test
    fun executesSelectedIndexMethodThroughTableExecution() {
        val manager = RecordingQueryExecute(ExecuteResult.Success(true))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createIndex {
                    tableName("lookup")
                    name("idx_lookup_code")
                    column("code")
                    using(IndexMethod.HASH)
                },
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        assertEquals("CREATE INDEX `idx_lookup_code` ON `lookup` (`code`) USING HASH", manager.executedQuery?.query)
        assertEquals(ExecuteResult.Success(true), received)
    }

    private class RecordingQueryExecute(
        private val result: ExecuteResult<Boolean>,
    ) : IQueryExecute {
        var executedQuery: BuiltQuery? = null

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            executedQuery = builtQuery
            blockExecute(result)
        }

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) =
            error("Unexpected select while executing CREATE INDEX")
        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected update while executing CREATE INDEX")
        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected insert while executing CREATE INDEX")
        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected delete while executing CREATE INDEX")
    }
}
