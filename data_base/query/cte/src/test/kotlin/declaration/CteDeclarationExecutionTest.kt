package gog.my_project.data_base.query.cte.declaration

import gog.my_project.collection.ICollection
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryConsumer
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.executer.result.error
import gog.my_project.data_base.query.executer.result.success
import gog.my_project.data_base.query.executer.result.toCollection
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CteDeclarationExecutionTest {

    @Test
    fun `prepared consumer exposes sql and metadata callbacks without executing`() {
        val declaration = ParameterizedDeclaration()
        val executor = RecordingExecutor()

        val consumer = declaration.prepare(executor, 7)
        var sqlText: String? = null
        var sqlValues: List<Any?> = emptyList()
        var outputAliases: List<String?> = emptyList()

        val returned = consumer
            .sql { built ->
                sqlText = built.query
                sqlValues = built.params.map { it.value }
            }
            .details { metadata ->
                outputAliases = metadata.outputs.map { it.publishedSqlAlias }
            }

        assertSame(consumer, returned)
        assertTrue(sqlText.orEmpty().contains(":requested_id"), sqlText)
        assertEquals(listOf(7), sqlValues)
        assertEquals(listOf("value"), outputAliases)
        assertEquals(listOf(7), declaration.createdParams)
        assertEquals(0, executor.selectExecutionCalls)

        val execution = assertIs<ExecuteResult.Failure>(returned.execute())
        assertEquals("database execution failed", execution.exception.message)
        assertEquals(1, executor.selectExecutionCalls)
        assertEquals(listOf(7), executor.selectQueries.map(::parameterValue))
    }

    @Test
    fun `direct executions pass each parameter to one fresh cte definition through executor`() {
        val declaration = ParameterizedDeclaration()
        val executor = RecordingExecutor()

        val firstResult = assertIs<ExecuteResult.Failure>(declaration.execute(executor, 11))
        val secondResult = assertIs<ExecuteResult.Failure>(declaration.execute(executor, 22))

        assertEquals(listOf(11, 22), declaration.createdParams)
        assertEquals(listOf(11, 22), executor.selectQueries.map(::parameterValue))
        assertEquals(2, executor.selectExecutionCalls)
        assertEquals("database execution failed", firstResult.exception.message)
        assertEquals("database execution failed", secondResult.exception.message)
    }

    @Test
    fun `typed execution configures one consumer and maps each row into a fresh output`() {
        val declaration = ParameterizedDeclaration()
        val executor = RecordingExecutor(
            selectResult = ExecuteResult.Success(
                resultSet(labels = listOf("value"), rows = listOf(listOf(41), listOf(42))),
            ),
        )
        var successCalls = 0
        var callbackRows: ICollection<ParameterizedDeclaration>? = null
        var mappedValues = emptyList<Int?>()
        var handledFailure: Throwable? = null

        val result: ExecuteResult<ICollection<ParameterizedDeclaration>> = declaration.execute(executor, 7)
            .success { rows ->
                successCalls++
                val typedRows: ICollection<ParameterizedDeclaration> = rows
                callbackRows = typedRows
                mappedValues = buildList { typedRows.forEach { add(it.value) } }
                rows
            }
            .error { handledFailure = it }
        (result as? ExecuteResult.Failure)?.let { failure ->
            println("ExecuteResult.Failure.exception:\n${failure.exception.stackTraceToString()}")
        }
        val outputs = assertNotNull(assertIs<ExecuteResult.Success<ICollection<ParameterizedDeclaration>>>(result).result)

        assertEquals(1, successCalls)
        assertNull(handledFailure)
        assertEquals(listOf(41, 42), mappedValues)
        assertEquals(listOf(41, 42), outputs.toList().map { it.value })
        assertSame(outputs, callbackRows)
        assertNotSame(declaration, outputs[0])
        assertNotSame(outputs[0], outputs[1])
        assertEquals(2, declaration.mappingCalls)
        assertEquals(listOf(7), declaration.createdParams)
        assertEquals(1, declaration.configureCalls)
        assertSame(declaration.consumerReceived, declaration.consumerConfigured)
        assertTrue(declaration.configuredSql.orEmpty().contains(":requested_id"))
        assertEquals(listOf("value"), declaration.configuredAliases)
        assertEquals(1, executor.selectExecutionCalls)
        assertEquals(listOf(7), executor.selectQueries.map(::parameterValue))

        val extracted: ICollection<ParameterizedDeclaration> = result.toCollection()
        assertSame(outputs, extracted)
        assertEquals(2, declaration.mappingCalls)
        assertEquals(1, executor.selectExecutionCalls)
    }

    @Test
    fun `execute override chains success and error callbacks once with typed rows`() {
        val declaration = ParameterizedDeclaration().apply { chainCallbacksInOverride = true }
        val executor = RecordingExecutor(
            selectResult = ExecuteResult.Success(
                resultSet(labels = listOf("value"), rows = listOf(listOf(51), listOf(52))),
            ),
        )

        val result: ExecuteResult<ICollection<ParameterizedDeclaration>> = declaration.execute(executor, 13)
        val outputs = assertNotNull(
            assertIs<ExecuteResult.Success<ICollection<ParameterizedDeclaration>>>(result).result,
        )

        assertEquals(1, declaration.overrideSuccessCalls)
        assertEquals(0, declaration.overrideErrorCalls)
        assertEquals(listOf(51, 52), declaration.overrideMappedValues)
        assertEquals(listOf(51, 52), outputs.toList().map { it.value })
        assertEquals(2, declaration.mappingCalls)
        assertEquals(listOf(13), declaration.createdParams)
        assertEquals(1, declaration.configureCalls)
        assertEquals(1, executor.selectExecutionCalls)
    }

    @Test
    fun `typed execution error chain observes failure without running success callback or executing again`() {
        val declaration = ParameterizedDeclaration()
        val queryFailure = IllegalStateException("database execution failed")
        val executor = RecordingExecutor(selectResult = ExecuteResult.Failure(queryFailure))
        var successCalls = 0
        var handledFailure: Throwable? = null

        val result: ExecuteResult<ICollection<ParameterizedDeclaration>> = declaration.execute(executor, 9)
            .success { rows ->
                successCalls++
                val typedRows: ICollection<ParameterizedDeclaration> = rows
                typedRows.forEach { it.value }
                rows
            }
            .error { handledFailure = it }

        val failure = assertIs<ExecuteResult.Failure>(result)
        assertSame(queryFailure, failure.exception)
        assertSame(queryFailure, handledFailure)
        assertEquals(0, successCalls)
        assertEquals(listOf(9), declaration.createdParams)
        assertEquals(1, declaration.configureCalls)
        assertEquals(1, executor.selectExecutionCalls)
        assertEquals(listOf(9), executor.selectQueries.map(::parameterValue))
    }

    @Test
    fun `typed execution preserves an empty mapped collection`() {
        val declaration = ParameterizedDeclaration()
        val executor = RecordingExecutor(
            selectResult = ExecuteResult.Success(resultSet(labels = listOf("value"), rows = emptyList())),
        )

        val result = declaration.execute(executor, 7)
        val outputs = assertNotNull(
            assertIs<ExecuteResult.Success<ICollection<ParameterizedDeclaration>>>(result).result,
        )

        assertEquals(0, outputs.size)
        assertEquals(listOf(7), declaration.createdParams)
        assertEquals(1, executor.selectExecutionCalls)
    }

    @Test
    fun `cte construction failure remains outside the executor result boundary`() {
        val executor = RecordingExecutor()
        val declaration = FailingDeclaration()

        val failure = assertFailsWith<IllegalStateException> {
            declaration.execute(executor, "query-params")
        }

        assertEquals("cte construction failed", failure.message)
        assertEquals(1, declaration.createCalls)
        assertEquals(0, executor.selectExecutionCalls)
    }

    private fun parameterValue(query: IQueryRenderSelectApi): Any? {
        val condition = query.ast.where!!.condition!!.conditions.single() as IQueryConditionsAst
        return (condition.conditionSideRight as IQueryParameterAst<*>).metadata.value
    }

    private fun resultSet(labels: List<String>, rows: List<List<Any?>>): ResultSet {
        var rowIndex = -1
        var lastWasNull = false
        var closed = false
        val metadata = proxy(ResultSetMetaData::class.java) { _, method, arguments ->
            when (method.name) {
                "getColumnCount" -> labels.size
                "getColumnLabel", "getColumnName" -> labels[(arguments!![0] as Int) - 1]
                "isNullable" -> ResultSetMetaData.columnNullableUnknown
                "toString" -> "CteDeclarationExecutionTestMetaData"
                else -> error("Unexpected ResultSet metadata call: ${method.name}")
            }
        }
        return proxy(ResultSet::class.java) { resultSet, method, arguments ->
            when (method.name) {
                "next" -> ++rowIndex < rows.size
                "getMetaData" -> metadata
                "getInt" -> {
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastWasNull = value == null
                    (value as? Number)?.toInt() ?: 0
                }
                "wasNull" -> lastWasNull
                "close" -> { closed = true; null }
                "isClosed" -> closed
                "toString" -> "CteDeclarationExecutionTestResultSet"
                "hashCode" -> System.identityHashCode(resultSet)
                "equals" -> resultSet === arguments?.get(0)
                else -> error("Unexpected ResultSet call: ${method.name}")
            }
        }
    }

    private fun <T> proxy(type: Class<T>, handler: InvocationHandler): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
}

private class ParameterizedDeclaration : CteDeclaration<Int, ParameterizedDeclaration>() {
    val createdParams = mutableListOf<Int>()
    var chainCallbacksInOverride = false
    var mappingCalls = 0
    var overrideSuccessCalls = 0
    var overrideErrorCalls = 0
    var overrideMappedValues = emptyList<Int?>()

    @QBQuerySelect(name = "value", alias = "value")
    var value: Int? = null
    var configureCalls: Int = 0
    var consumerReceived: QueryConsumer? = null
    var consumerConfigured: QueryConsumer? = null
    var configuredSql: String? = null
    var configuredAliases: List<String?> = emptyList()

    override fun configureConsumer(consumer: QueryConsumer): QueryConsumer {
        configureCalls++
        consumerReceived = consumer
        return consumer
            .sql { built -> configuredSql = built.query }
            .details { metadata -> configuredAliases = metadata.outputs.map { it.publishedSqlAlias } }
            .also { consumerConfigured = it }
    }

    override fun fromQueryRow(row: QueryRow): ParameterizedDeclaration =
        super.fromQueryRow(row).also { mappingCalls++ }

    override fun execute(
        queryManager: IQueryBuilderExecutor,
        params: Int,
    ): ExecuteResult<ICollection<ParameterizedDeclaration>> {
        val result = super.execute(queryManager, params)
        if (!chainCallbacksInOverride) return result

        return result
            .success { rows ->
                overrideSuccessCalls++
                val typedRows: ICollection<ParameterizedDeclaration> = rows
                overrideMappedValues = buildList { typedRows.forEach { add(it.value) } }
                rows
            }
            .error { overrideErrorCalls++ }
    }

    override fun createCte(params: Int): Cte {
        createdParams += params
        return buildCte {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableAttribute("1") }
                    alias("value")
                    execute(DataType.INT)
                }
            }
            where {
                conditions {
                    addCondition {
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("requested_id")
                            paramValue(params)
                        }
                    }
                }
            }
        }
    }

    fun prepare(executor: IQueryBuilderExecutor, params: Int): QueryConsumer =
        queryReady(executor, params)
}

private class FailingDeclaration : CteDeclaration<String, FailingDeclaration>() {
    var createCalls = 0

    override fun createCte(params: String): Cte {
        createCalls++
        error("cte construction failed")
    }
}

private class RecordingExecutor(
    private val selectResult: ExecuteResult<ResultSet> =
        ExecuteResult.Failure(IllegalStateException("database execution failed")),
) : IQueryBuilderExecutor {
    var selectExecutionCalls = 0
    val selectQueries = mutableListOf<IQueryRenderSelectApi>()

    override fun execute(
        queryBuilder: IQueryRenderSelectApi,
        blockExecute: (ExecuteResult<ResultSet>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) {
        selectExecutionCalls++
        selectQueries += queryBuilder
        blockExecute(selectResult)
    }

    override fun execute(
        queryBuilder: IQueryRenderInsertApi,
        blockExecute: (ExecuteResult<Long>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = error("Unexpected INSERT execution")

    override fun execute(
        queryBuilder: IQueryRenderUpdateApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = error("Unexpected UPDATE execution")

    override fun execute(
        queryBuilder: IQueryRenderDeleteApi,
        blockExecute: (ExecuteResult<Int>) -> Unit,
        blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
    ) = error("Unexpected DELETE execution")
}
