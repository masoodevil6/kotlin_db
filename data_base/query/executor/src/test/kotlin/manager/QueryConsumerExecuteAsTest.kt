package gog.my_project.data_base.query.executer.manager

import gog.my_project.collection.ICollection
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryConsumer
import gog.my_project.data_base.query.executer.result.QueryRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QueryConsumerExecuteAsTest {
    @Test
    fun `executeAs maps rows once in order into ICollection`() {
        val executor = RecordingExecutor(
            ExecuteResult.Success(
                listOf(
                    QueryRow(listOf("id" to 3)),
                    QueryRow(listOf("id" to 1)),
                    QueryRow(listOf("id" to 2)),
                ),
            ),
        )
        val mappedIds = mutableListOf<Int>()

        val result: ExecuteResult<ICollection<Int>> = executor
            .queryBuilder(QueryRenderSelectBuilder())
            .executeAs { row ->
                (row.getValue("id") as Int).also(mappedIds::add)
            }

        val outputs = assertNotNull(assertIs<ExecuteResult.Success<ICollection<Int>>>(result).result)
        assertEquals(listOf(3, 1, 2), mappedIds)
        assertEquals(listOf(3, 1, 2), outputs.toList())
        assertEquals(1, executor.getCalls)
    }

    @Test
    fun `executeAs returns an empty collection without invoking mapper`() {
        val executor = RecordingExecutor(ExecuteResult.Success(emptyList()))
        var mapperCalls = 0

        val result = executor.queryBuilder(QueryRenderSelectBuilder()).executeAs {
            mapperCalls++
            it
        }

        val outputs = assertNotNull(assertIs<ExecuteResult.Success<ICollection<QueryRow>>>(result).result)
        assertEquals(0, outputs.size)
        assertEquals(0, mapperCalls)
        assertEquals(1, executor.getCalls)
    }

    @Test
    fun `executeAs preserves query failure without invoking mapper`() {
        val cause = IllegalArgumentException("query failed")
        val executor = RecordingExecutor(ExecuteResult.Failure(cause))
        var mapperCalls = 0

        val result = executor.queryBuilder(QueryRenderSelectBuilder()).executeAs {
            mapperCalls++
            it
        }

        assertSame(cause, assertIs<ExecuteResult.Failure>(result).exception)
        assertEquals(0, mapperCalls)
        assertEquals(1, executor.getCalls)
    }

    @Test
    fun `executeAs does not invoke sql or details callbacks again`() {
        val executor = RecordingExecutor(
            ExecuteResult.Success(listOf(QueryRow(listOf("id" to 7)))),
        )
        val query = QueryRenderSelectBuilder().select {
            addColumn {
                column { tableAttribute("1") }
                alias("id")
                execute(DataType.INT)
            }
        }
        val consumer = executor.queryBuilder(query)
        var sqlCallbackCalls = 0
        var detailsCallbackCalls = 0

        consumer
            .sql { sqlCallbackCalls++ }
            .details { detailsCallbackCalls++ }
            .executeAs { it.getValue("id") as Int }

        assertEquals(1, sqlCallbackCalls)
        assertEquals(1, detailsCallbackCalls)
        assertEquals(1, executor.getCalls)
    }

    @Test
    fun `executeAs returns mapping failure with row context and stops iteration`() {
        val mappingCause = IllegalArgumentException("invalid output")
        val executor = RecordingExecutor(
            ExecuteResult.Success(
                listOf(
                    QueryRow(listOf("id" to 1)),
                    QueryRow(listOf("id" to 2)),
                    QueryRow(listOf("id" to 3)),
                ),
            ),
        )
        val mappedIds = mutableListOf<Int>()

        val result = executor.queryBuilder(QueryRenderSelectBuilder()).executeAs { row ->
            val id = row.getValue("id") as Int
            mappedIds += id
            if (id == 2) throw mappingCause
            id
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("row 2"))
        assertSame(mappingCause, failure.cause)
        assertEquals(listOf(1, 2), mappedIds)
        assertEquals(1, executor.getCalls)
    }

    @Test
    fun `executeAs rejects a successful execution without a row list`() {
        val executor = RecordingExecutor(ExecuteResult.Success(null))
        var mapperCalls = 0

        val result = executor.queryBuilder(QueryRenderSelectBuilder()).executeAs {
            mapperCalls++
            it
        }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("materialized row list"))
        assertEquals(0, mapperCalls)
        assertEquals(1, executor.getCalls)
    }

    @Test
    fun `executeAs reports missing materialized rows to the scoped failure observer`() {
        val executor = RecordingExecutor(ExecuteResult.Success(null))
        val observedFailures = mutableListOf<Throwable>()

        val result = QueryConsumer(
            QueryRenderSelectBuilder(),
            executor,
            observedFailures::add,
        ).executeAs { it }

        val failure = assertIs<ExecuteResult.Failure>(result).exception
        assertTrue(failure.message.orEmpty().contains("materialized row list"))
        assertEquals(1, observedFailures.size)
        assertSame(failure, observedFailures.single())
        assertEquals(1, executor.getCalls)
    }

    private class RecordingExecutor(
        private val selectResult: ExecuteResult<List<QueryRow>>,
    ) : IQueryBuilderExecutor {
        var getCalls: Int = 0
            private set

        override fun get(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<List<QueryRow>>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            getCalls++
            blockExecute(selectResult)
        }

        override fun execute(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<java.sql.ResultSet>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("This test uses the materialized SELECT get path")

        override fun execute(
            queryBuilder: IQueryRenderInsertApi,
            blockExecute: (ExecuteResult<Long>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("INSERT is not used by this test")

        override fun execute(
            queryBuilder: IQueryRenderUpdateApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("UPDATE is not used by this test")

        override fun execute(
            queryBuilder: IQueryRenderDeleteApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("DELETE is not used by this test")
    }
}
