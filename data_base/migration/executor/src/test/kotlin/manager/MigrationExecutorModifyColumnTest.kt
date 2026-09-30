package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.builder.modifyColumn
import gog.my_project.data_base.migration.params.data_types.IntType
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorModifyColumnTest {
    @Test
    fun sendsRenderedSqlThroughExistingManagerAndPreservesFailure() {
        val failure = IllegalStateException("database rejected MODIFY")
        val manager = RecordingQueryExecute(ExecuteResult.Failure(failure))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null
        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(modification(), blockExecute = { received = it })
        } finally {
            DefaultDatabaseConfig.config = previous
        }
        assertEquals("ALTER TABLE `users` MODIFY COLUMN `age` INT NOT NULL", manager.query?.query)
        assertSame(failure, (received as ExecuteResult.Failure).exception)
    }

    private fun modification() = modifyColumn {
        tableName("users")
        name("age")
        dataType(IntType())
        notNull()
        autoIncrement(false)
    }

    private class RecordingQueryExecute(
        private val result: ExecuteResult<Boolean>,
    ) : IQueryExecute {
        var query: BuiltQuery? = null
        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            query = builtQuery
            blockExecute(result)
        }
        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) =
            error("Unexpected select")
        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected update")
        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected insert")
        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected delete")
    }
}
