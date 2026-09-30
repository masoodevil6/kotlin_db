package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.builder.createFullTextIndex
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorCreateFullTextIndexTest {
    @Test
    fun delegatesToExecuteTableWithoutPreflight() {
        val manager = RecordingQueryExecute(ExecuteResult.Success(true))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createFullTextIndex {
                    tableName("posts")
                    name("ft_posts_title_body")
                    columns("title", "body")
                },
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        assertEquals(
            "CREATE FULLTEXT INDEX `ft_posts_title_body` ON `posts` (`title`, `body`)",
            manager.executedQuery?.query,
        )
        assertEquals(ExecuteResult.Success(true), received)
    }

    @Test
    fun preservesManagerFailureAndReportsQuery() {
        val failure = IllegalStateException("unsupported fulltext column")
        val manager = RecordingQueryExecute(ExecuteResult.Failure(failure))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null
        var reportedQuery: String? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createFullTextIndex {
                    tableName("posts")
                    name("ft_posts_body")
                    columns("body")
                },
                blockExecute = { received = it },
                blockQueryInfo = { query, _ -> reportedQuery = query },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        val expected = "CREATE FULLTEXT INDEX `ft_posts_body` ON `posts` (`body`)"
        assertEquals(expected, manager.executedQuery?.query)
        assertEquals(StringTools.formatSql(expected), reportedQuery)
        assertSame(failure, (received as ExecuteResult.Failure).exception)
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
            error("Unexpected preflight select while executing FULLTEXT index")
        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected update while executing FULLTEXT index")
        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected insert while executing FULLTEXT index")
        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected delete while executing FULLTEXT index")
    }
}
