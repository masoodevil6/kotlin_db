package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.builder.createForeignKey
import gog.my_project.data_base.migration.builder.dropForeignKey
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorForeignKeyTest {
    @Test
    fun createForeignKeyExecutesViaExecuteTableAndPropagatesSuccess() {
        val manager = RecordingQueryExecute(ExecuteResult.Success(true))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var result: ExecuteResult<Boolean>? = null
        var queryInfo: String? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = createForeignKey {
                    tableName("orders"); name("fk_orders_user"); columns("user_id")
                    referencesTable("users"); referencesColumns("id")
                },
                blockExecute = { result = it },
                blockQueryInfo = { query, _ -> queryInfo = query },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        val expected = "ALTER TABLE `orders` ADD CONSTRAINT `fk_orders_user` " +
            "FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)"
        assertEquals(expected, manager.executedQuery?.query)
        assertEquals(StringTools.formatSql(expected), queryInfo)
        assertEquals(ExecuteResult.Success(true), result)
        assertEquals(1, manager.executeTableCalls)
        assertEquals(0, manager.executeSelectCalls)
    }

    @Test
    fun dropForeignKeyExecutesViaExecuteTableAndPropagatesFailure() {
        val failure = IllegalStateException("foreign key does not exist")
        val manager = RecordingQueryExecute(ExecuteResult.Failure(failure))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var result: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MARIA_DB)
            executor.execute(
                queryBuilder = dropForeignKey { tableName("orders"); name("fk_orders_user") },
                blockExecute = { result = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        val expected = "ALTER TABLE `orders` DROP FOREIGN KEY `fk_orders_user`"
        assertEquals(expected, manager.executedQuery?.query)
        assertSame(failure, (result as ExecuteResult.Failure).exception)
        assertEquals(1, manager.executeTableCalls)
        assertEquals(0, manager.executeSelectCalls)
    }

    private class RecordingQueryExecute(
        private val executeResult: ExecuteResult<Boolean>,
    ) : IQueryExecute {
        var executedQuery: BuiltQuery? = null
        var executeTableCalls = 0
        var executeSelectCalls = 0

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            executeTableCalls++
            executedQuery = builtQuery
            blockExecute(executeResult)
        }

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
            executeSelectCalls++
            error("Foreign Key DDL must not perform schema introspection")
        }

        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected update while executing Foreign Key DDL")

        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected insert while executing Foreign Key DDL")

        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected delete while executing Foreign Key DDL")
    }
}
