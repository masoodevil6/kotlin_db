package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base_schema.builder.ast.add_column.SchemaAddColumnBuilder
import gog.my_project.data_base_schema.params.data_types.IntType
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationExecutorAddColumnTest {
    @Test
    fun `add column renders and executes through operation schema`() {
        val previousConfig = DefaultDatabaseConfig.config
        val manager = RecordingQueryExecute()
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previousConfig.copy(dialect = DialectQuery.MY_SQL)
            MigrationExecutor(queryExecutor = manager).execute(
                queryBuilder = SchemaAddColumnBuilder().apply {
                    tableName("users")
                    name("age")
                    dataType(IntType())
                    notNull()
                },
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previousConfig
        }

        assertEquals("ALTER TABLE `users` ADD COLUMN `age` INT NOT NULL", manager.executedQuery?.query)
        assertEquals(ExecuteResult.Success(true), received)
    }

    private class RecordingQueryExecute : IQueryExecute {
        var executedQuery: BuiltQuery? = null

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            executedQuery = builtQuery
            blockExecute(ExecuteResult.Success(true))
        }

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) =
            error("Unexpected SELECT while executing ADD COLUMN")

        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected UPDATE while executing ADD COLUMN")

        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected INSERT while executing ADD COLUMN")

        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected DELETE while executing ADD COLUMN")
    }
}
