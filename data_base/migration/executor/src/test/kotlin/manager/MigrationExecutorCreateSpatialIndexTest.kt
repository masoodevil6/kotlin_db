package gog.my_project.data_base.migration.executor.manager

import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.dialect.DialectQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.interfaces.IQueryExecute
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.migration.builder.createSpatialIndex
import gog.my_project.tools.scripts.StringTools
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class MigrationExecutorCreateSpatialIndexTest {
    @Test
    fun delegatesSuccessToExecuteTableWithoutSelect() {
        val manager = RecordingQueryExecute(ExecuteResult.Success(true))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MY_SQL)
            executor.execute(
                queryBuilder = spatialIndex(),
                blockExecute = { received = it },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        assertEquals("CREATE SPATIAL INDEX `spx_places_geom` ON `places` (`geom`)", manager.executedQuery?.query)
        assertEquals(ExecuteResult.Success(true), received)
        assertEquals(1, manager.executeTableCalls)
        assertEquals(0, manager.executeSelectCalls)
    }

    @Test
    fun preservesFailureAndReportsQueryWithoutSelect() {
        val failure = IllegalStateException("spatial index is not supported by this table")
        val manager = RecordingQueryExecute(ExecuteResult.Failure(failure))
        val executor = MigrationExecutor(queryExecutor = manager)
        val previous = DefaultDatabaseConfig.config
        var received: ExecuteResult<Boolean>? = null
        var reportedQuery: String? = null

        try {
            DefaultDatabaseConfig.config = previous.copy(dialect = DialectQuery.MARIA_DB)
            executor.execute(
                queryBuilder = spatialIndex(),
                blockExecute = { received = it },
                blockQueryInfo = { query, _ -> reportedQuery = query },
            )
        } finally {
            DefaultDatabaseConfig.config = previous
        }

        val expected = "CREATE SPATIAL INDEX `spx_places_geom` ON `places` (`geom`)"
        assertEquals(expected, manager.executedQuery?.query)
        assertEquals(StringTools.formatSql(expected), reportedQuery)
        assertSame(failure, (received as ExecuteResult.Failure).exception)
        assertEquals(1, manager.executeTableCalls)
        assertEquals(0, manager.executeSelectCalls)
    }

    private fun spatialIndex() = createSpatialIndex {
        tableName("places")
        name("spx_places_geom")
        column("geom")
    }

    private class RecordingQueryExecute(
        private val result: ExecuteResult<Boolean>,
    ) : IQueryExecute {
        var executedQuery: BuiltQuery? = null
        var executeTableCalls = 0
        var executeSelectCalls = 0

        override fun executeTable(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Boolean>) -> Unit) {
            executeTableCalls++
            executedQuery = builtQuery
            blockExecute(result)
        }

        override fun executeSelect(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<ResultSet>) -> Unit) {
            executeSelectCalls++
            error("Spatial index execution must not perform schema introspection")
        }

        override fun executeUpdate(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected update while executing SPATIAL index")

        override fun executeInsert(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Long>) -> Unit) =
            error("Unexpected insert while executing SPATIAL index")

        override fun executeDelete(builtQuery: BuiltQuery, blockExecute: (ExecuteResult<Int>) -> Unit) =
            error("Unexpected delete while executing SPATIAL index")
    }
}
