package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import java.sql.ResultSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@QBQuery(name = "details_query", alias = "details_source")
private class DetailsQueryDeclaration : CteDeclaration<Unit>(Unit) {
    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias("value")
            }
        }
    }
}

@QBQuery(name = "typed_details_query", alias = "typed_details_source")
private class TypedDetailsQueryDeclaration : CteDeclaration<Unit>(Unit) {
    @gog.my_project.data_base.core.annotations.models.QBQuerySelect(
        name = "logical_value",
        alias = "typed_value",
    )
    val value: Long
        get() = error("Metadata extraction must not invoke output getters")

    override fun createCte(params: Unit): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(TypedDetailsQueryDeclaration::value)
                execute(DataType.LONG)
            }
        }
    }
}

class QueryConsumerTest {

    @Test
    fun `queryBuilder configures one builder and binds the receiving executor`() {
        val row = QueryRow(listOf("user_id" to 17))
        val executor = RecordingExecutor(firstResult = ExecuteResult.Success(row))
        var callbackQuery: IQueryRenderSelectApi? = null

        val consumer = executor.queryBuilder { operations ->
            operations.querySelect { query ->
                callbackQuery = query
                query
                    .select {
                        addColumn {
                            column { tableColumn("users", "id") }
                            alias("user_id")
                        }
                    }
                    .from { table("users").alias("u") }
                    .where { }
            }
        }

        assertIs<ExecuteResult.Success<QueryRow>>(consumer.first())
        assertSame(callbackQuery, executor.firstQuery)
        assertEquals(1, executor.firstCalls)
        assertTrue(callbackQuery?.ast?.select != null)
        assertTrue(callbackQuery?.ast?.table != null)
        assertTrue(callbackQuery?.ast?.where != null)
    }

    @Test
    fun `consumer delegates execute and sql using its stored query`() {
        val executor = RecordingExecutor(
            getResult = ExecuteResult.Success(emptyList()),
            sqlResult = "SELECT users.id AS user_id",
        )
        var callbackQuery: IQueryRenderSelectApi? = null
        val consumer = executor.queryBuilder { operations ->
            operations.querySelect { query ->
                callbackQuery = query
                query.select {
                    addColumn {
                        column { tableColumn("users", "id") }
                        alias("user_id")
                    }
                }
            }
        }

        assertEquals(emptyList(), assertIs<ExecuteResult.Success<List<QueryRow>>>(consumer.execute()).result)
        assertEquals("SELECT users.id AS user_id", consumer.sql().query)
        assertSame(callbackQuery, executor.getQuery)
        assertSame(callbackQuery, executor.sqlQuery)
        assertEquals(1, executor.getCalls)
        assertEquals(1, executor.sqlCalls)
    }

    @Test
    fun `details reads effective metadata without rendering SQL and keeps the consumer chain`() {
        val executor = RecordingExecutor(sqlResult = "SELECT scripted")
        val consumer = executor.queryBuilderSelect { query ->
            query
                .from { cte(DetailsQueryDeclaration()).alias("details_occurrence") }
                .select {
                    addColumn {
                        column { tableAttribute("1") }
                        alias("result")
                        execute(DataType.LONG)
                    }
                }
        }

        val metadata = consumer.details()

        assertEquals(1, metadata.relations.size)
        assertEquals("details_query", metadata.relations.single().logicalQueryName)
        assertEquals("details_query", metadata.relations.single().cteRuntimeName)
        assertEquals(null, metadata.relations.single().withSqlName)
        assertEquals("details_occurrence", metadata.relations.single().occurrenceAlias)
        assertEquals(listOf("value"), metadata.relations.single().outputs.map { it.publishedSqlAlias })
        assertEquals(listOf("result"), metadata.outputs.map { it.publishedSqlAlias })
        assertEquals(listOf(DataType.LONG), metadata.outputs.map { it.executionType })
        assertEquals(0, executor.sqlCalls)
        assertEquals(0, executor.firstCalls)
        assertEquals(0, executor.getCalls)

        assertSame(consumer, consumer.details { observed -> assertEquals(metadata, observed) })
    }

    @Test
    fun `details fails clearly for a custom select without a metadata provider while SQL remains available`() {
        val executor = RecordingExecutor(sqlResult = "SELECT scripted")
        val delegate = QueryRenderSelectBuilder()
        val customQuery = object : IQueryRenderSelectApi by delegate {}
        val consumer = executor.queryBuilder(customQuery)

        val failure = assertFailsWith<IllegalStateException> { consumer.details() }
        assertTrue(failure.message.orEmpty().contains("EffectiveQueryMetadata"))
        assertEquals("SELECT scripted", consumer.sql().query)
        assertEquals(1, executor.sqlCalls)
    }

    @Test
    fun `details remains available when an existing builder is bound to the executor`() {
        val executor = RecordingExecutor()
        val query = QueryRenderSelectBuilder()
            .from { cte(DetailsQueryDeclaration()).alias("existing_occurrence") }

        val consumer = executor.queryBuilder(query)

        assertEquals("existing_occurrence", consumer.details().relations.single().occurrenceAlias)
    }

    @Test
    fun `details returns the same metadata before and after SQL inspection`() {
        val executor = RecordingExecutor(sqlResult = "SELECT scripted")
        val consumer = executor.queryBuilderSelect { query ->
            query.from { cte(DetailsQueryDeclaration()).alias("snapshot_occurrence") }
        }

        val beforeSql = consumer.details()
        assertEquals(0, executor.sqlCalls)

        assertEquals("SELECT scripted", consumer.sql().query)
        assertEquals(1, executor.sqlCalls)

        val afterSql = consumer.details()
        assertEquals(beforeSql, afterSql)
        assertEquals(1, executor.sqlCalls)
    }

    @Test
    fun `details sql and execute preserve metadata SQL and parameters in one flow`() {
        val executor = RecordingExecutor(
            getResult = ExecuteResult.Success(listOf(QueryRow(listOf("result" to 7)))),
            sqlResult = "SELECT :flow_value",
        )
        var queryReference: IQueryRenderSelectApi? = null
        val consumer = executor.queryBuilderSelect { query ->
            queryReference = query
            query
                .from { cte(DetailsQueryDeclaration()).alias("flow_source") }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableAttribute("1") }
                            operationEqual()
                            sideValue("flow_value", 7)
                        }
                    }
                }
        }

        val parametersBeforeDetails = queryReference!!.params.map { it.name to it.value }
        val metadata = consumer.details()

        assertEquals(0, executor.sqlCalls)
        assertEquals("flow_source", metadata.relations.single().occurrenceAlias)

        val built = consumer.sql()
        assertEquals("SELECT :flow_value", built.query)
        assertEquals(parametersBeforeDetails, built.params.map { it.name to it.value })

        val execution = consumer.execute()
        assertEquals(1, executor.getCalls)
        val result = assertIs<ExecuteResult.Success<List<QueryRow>>>(execution).result.orEmpty()
        assertEquals(7, result.single()["result"])
        assertEquals(parametersBeforeDetails, queryReference!!.params.map { it.name to it.value })
    }

    @Test
    fun `details does not change typed execution metadata or returned result`() {
        val executor = RecordingExecutor(
            firstResult = ExecuteResult.Success(QueryRow(listOf("typed_value" to 11L))),
        )
        val consumer = executor.queryBuilderSelect { query ->
            query.from { cte(TypedDetailsQueryDeclaration()).alias("typed_source") }
        }

        val metadata = consumer.details()
        assertEquals(DataType.LONG, metadata.relations.single().outputs.single().executionType)

        val result = assertIs<ExecuteResult.Success<QueryRow>>(consumer.first()).result
        assertEquals(11L, result?.get("typed_value"))
        assertEquals(DataType.LONG, executor.firstQuery?.ast?.table?.derivedQuery?.select?.columns?.single()?.ExecutionType)
        assertEquals(1, executor.firstCalls)
    }

    @Test
    fun `details observes query state at call time after SQL inspection`() {
        val executor = RecordingExecutor(sqlResult = "SELECT before")
        var queryReference: IQueryRenderSelectApi? = null
        val consumer = executor.queryBuilderSelect { query ->
            queryReference = query
            query
        }

        assertEquals("SELECT before", consumer.sql().query)
        queryReference!!.from { cte(DetailsQueryDeclaration()).alias("changed_source") }

        val afterChange = consumer.details()
        assertEquals("changed_source", afterChange.relations.single().occurrenceAlias)
        assertEquals(1, executor.sqlCalls)
    }

    @Test
    fun `success transforms value and supports inferred nullable result type`() {
        val row = QueryRow(listOf("user_id" to 17))
        val transformed: ExecuteResult<Any?> = ExecuteResult.Success(row).success { it["user_id"] }

        assertEquals(17, assertIs<ExecuteResult.Success<Any?>>(transformed).result)

        var transformCalled = false
        val noRow: ExecuteResult<String> = ExecuteResult.Success<QueryRow>(null).success {
            transformCalled = true
            it["user_id"].toString()
        }
        assertFalse(transformCalled)
        assertNull(assertIs<ExecuteResult.Success<String>>(noRow).result)
    }

    @Test
    fun `success preserves failure and propagates transformer exception`() {
        val failure = IllegalArgumentException("query failed")
        val preserved = ExecuteResult.Failure(failure).success { it.toString() }
        assertSame(failure, assertIs<ExecuteResult.Failure>(preserved).exception)

        val transformFailure = IllegalStateException("transform failed")
        val actual = assertFailsWith<IllegalStateException> {
            ExecuteResult.Success("value").success<String, String> { throw transformFailure }
        }
        assertSame(transformFailure, actual)
    }

    @Test
    fun `error observes only failure and preserves result for chaining`() {
        val failure = IllegalArgumentException("query failed")
        var observed: Throwable? = null
        val original: ExecuteResult<String> = ExecuteResult.Failure(failure)

        val returned = original.error { observed = it }

        assertSame(failure, observed)
        assertSame(original, returned)

        var successHandlerCalled = false
        val success: ExecuteResult<String> = ExecuteResult.Success("ok")
        assertSame(success, success.error { successHandlerCalled = true })
        assertFalse(successHandlerCalled)
    }

    @Test
    fun `error handler exception propagates`() {
        val handlerFailure = IllegalStateException("handler failed")
        val actual = assertFailsWith<IllegalStateException> {
            ExecuteResult.Failure(RuntimeException("query failed")).error { throw handlerFailure }
        }
        assertSame(handlerFailure, actual)
    }

    @Test
    fun `vision fluent chain transforms and observes the same result`() {
        val executor = RecordingExecutor(
            firstResult = ExecuteResult.Success(QueryRow(listOf("user_id" to 17))),
        )
        var observedError: Throwable? = null

        val result: ExecuteResult<Any?> = executor
            .queryBuilder { operations ->
                operations.querySelect { query ->
                    query
                        .select {
                            addColumn {
                                column { tableColumn("users", "id") }
                                alias("user_id")
                            }
                        }
                        .from { table("users").alias("u") }
                        .where { }
                }
            }
            .first()
            .success { row -> row["user_id"] }
            .error { error -> observedError = error }

        assertEquals(17, assertIs<ExecuteResult.Success<Any?>>(result).result)
        assertNull(observedError)
        assertEquals(1, executor.firstCalls)
    }

    private class RecordingExecutor(
        private val firstResult: ExecuteResult<QueryRow> = ExecuteResult.Success(null),
        private val getResult: ExecuteResult<List<QueryRow>> = ExecuteResult.Success(emptyList()),
        private val sqlResult: String = "SELECT scripted",
    ) : IQueryBuilderExecutor {
        var firstCalls = 0
        var getCalls = 0
        var sqlCalls = 0
        var firstQuery: IQueryRenderSelectApi? = null
        var getQuery: IQueryRenderSelectApi? = null
        var sqlQuery: IQueryRenderSelectApi? = null

        override fun first(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<QueryRow>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            firstCalls++
            firstQuery = queryBuilder
            blockExecute(firstResult)
        }

        override fun get(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<List<QueryRow>>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            getCalls++
            getQuery = queryBuilder
            blockExecute(getResult)
        }

        override fun sql(queryBuilder: IQueryRenderSelectApi): String {
            sqlCalls++
            sqlQuery = queryBuilder
            return sqlResult
        }

        override fun execute(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<ResultSet>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("The Smart SELECT methods are overridden by this recording executor")

        override fun execute(
            queryBuilder: IQueryRenderInsertApi,
            blockExecute: (ExecuteResult<Long>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used in this test")

        override fun execute(
            queryBuilder: IQueryRenderUpdateApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used in this test")

        override fun execute(
            queryBuilder: IQueryRenderDeleteApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used in this test")
    }
}
