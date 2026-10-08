package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.api.delete_api.query_render_delete.IQueryRenderDeleteApi
import gog.my_project.data_base.query.api.interfaces.api.insert_api.query_render_insert.IQueryRenderInsertApi
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.api.update_api.query_render_update.IQueryRenderUpdateApi
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.relations.queryRelation as buildQueryRelationDefinition
import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

@QBTable(name = "materialized_users")
private class MaterializedUser : IModelBase {
    @QBColumn(name = "id", alias = "user_id")
    val id: Int = 0

    @QBColumn(name = "name", alias = "user_name")
    val name: String = ""
}

@QBTable(name = "other_users")
private class OtherMaterializedUser : IModelBase {
    @QBColumn(name = "id", alias = "other_user_id")
    val id: Int = 0
}

@QBTable(name = "unsupported_users")
private class UnsupportedMaterializedUser : IModelBase {
    @QBColumn(name = "enabled", alias = "enabled")
    val enabled: Boolean = false
}

private class MaterializedRelation : IQueryRelation<Unit> {
    override val relationName = "materialized_relation"

    val fullName: String?
        get() = error("Typed Relation output getter must not be invoked")

    override fun queryRelation(params: Unit): QueryRelation = buildQueryRelationDefinition(
        name = relationName,
        declarationOwner = MaterializedRelation::class,
    ) {
        table { table("source_users").alias("source") }
        select {
            addColumn {
                column { tableColumn("source", "full_name") }
                alias(MaterializedRelation::fullName)
            }
        }
    }
}

class SmartQueryResultTest {

    @Test
    fun `first materializes one row by alias and closes result set before consumer callback`() {
        val script = resultSet(
            labels = listOf("user_id", "user_name"),
            rows = listOf(listOf(7, "Ada"), listOf(8, "Lin")),
        )
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        var callbackSawClosedResultSet = false

        executor.first(select(
            Output("user_id", DataType.LONG),
            Output("user_name", DataType.STRING),
        ), blockExecute = { result ->
            callbackSawClosedResultSet = script.closed
            val row = assertIs<ExecuteResult.Success<QueryRow>>(result).result
            assertEquals(7L, row?.get("user_id"))
            assertIs<Long>(row?.get("user_id"))
            assertEquals("Ada", row?.get("user_name"))
        })

        assertTrue(callbackSawClosedResultSet)
        assertEquals(1, executor.selectExecutions)
        assertEquals(1, script.rowsRead)
        assertEquals(0, script.objectReads)
    }

    @Test
    fun `first reports no rows as successful null`() {
        val script = resultSet(listOf("user_id"), emptyList())
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        var outcome: ExecuteResult<QueryRow>? = null

        executor.first(select(Output("user_id", DataType.LONG)), blockExecute = { outcome = it })

        assertNull(assertIs<ExecuteResult.Success<QueryRow>>(outcome).result)
        assertTrue(script.closed)
    }

    @Test
    fun `first forwards query info through the existing executor path`() {
        val script = resultSet(listOf("user_id"), listOf(listOf(7)))
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        var reportedSql: String? = null
        var reportedParams: MutableMap<String, Any?>? = null

        executor.first(
            queryBuilder = select(Output("user_id", DataType.LONG)),
            blockExecute = { assertIs<ExecuteResult.Success<QueryRow>>(it) },
            blockQueryInfo = { sql, params ->
                reportedSql = sql
                reportedParams = params
            },
        )

        assertEquals("SELECT scripted", reportedSql)
        assertTrue(reportedParams == mapOf<String, Any?>("id" to 7))
        assertTrue(script.closed)
    }

    @Test
    fun `successful low-level result without a ResultSet is a failure not an empty result`() {
        val executor = RecordingExecutor(ExecuteResult.Success(null))
        var outcome: ExecuteResult<QueryRow>? = null

        executor.first(select(Output("user_id", DataType.LONG)), blockExecute = { outcome = it })

        assertIs<ExecuteResult.Failure>(outcome)
    }

    @Test
    fun `get materializes all rows and preserves SQL null`() {
        val script = resultSet(
            labels = listOf("user_id", "user_name"),
            rows = listOf(listOf(7, "Ada"), listOf(8, null)),
        )
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))

        executor.get(select(
            Output("user_id", DataType.LONG),
            Output("user_name", DataType.STRING),
        ), blockExecute = { result ->
            val rows = assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result.orEmpty()
            assertEquals(2, rows.size)
            assertEquals(7L, rows[0]["user_id"])
            assertEquals("Ada", rows[0]["user_name"])
            assertNull(rows[1]["user_name"])
            assertIs<Long>(rows[0]["user_id"])
        })

        assertTrue(script.closed)
        assertEquals(2, script.rowsRead)
        assertEquals(0, script.objectReads)
    }

    @Test
    fun `get reports zero rows as successful empty list`() {
        val script = resultSet(listOf("user_id"), emptyList())
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        var outcome: ExecuteResult<List<QueryRow>>? = null

        executor.get(select(Output("user_id", DataType.LONG)), blockExecute = { outcome = it })

        assertEquals(emptyList(), assertIs<ExecuteResult.Success<List<QueryRow>>>(outcome).result)
        assertTrue(script.closed)
    }

    @Test
    fun `int extraction preserves zero and SQL null using typed JDBC access`() {
        val script = resultSet(
            labels = listOf("user_age"),
            rows = listOf(listOf(0), listOf(null)),
        )
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))

        executor.get(select(Output("user_age", DataType.INT)), blockExecute = { result ->
            val rows = assertIs<ExecuteResult.Success<List<QueryRow>>>(result).result.orEmpty()
            assertEquals(0, rows[0]["user_age"])
            assertIs<Int>(rows[0]["user_age"])
            assertNull(rows[1]["user_age"])
        })

        assertEquals(2, script.typedReads)
        assertEquals(0, script.objectReads)
        assertTrue(script.closed)
    }

    @Test
    fun `first and get reject missing blank and duplicate aliases before execution`() {
        val resultSet = resultSet(listOf("same"), listOf(listOf(1)))
        val executor = RecordingExecutor(ExecuteResult.Success(resultSet.resultSet))

        val missingAlias = select(Output(null, DataType.LONG))
        executor.first(missingAlias, blockExecute = { assertIs<ExecuteResult.Failure>(it) })
        val blankAlias = select(Output("", DataType.LONG))
        executor.get(blankAlias, blockExecute = { assertIs<ExecuteResult.Failure>(it) })
        val duplicateAliases = select(Output("same", DataType.LONG), Output("same", DataType.INT))
        executor.first(duplicateAliases, blockExecute = { assertIs<ExecuteResult.Failure>(it) })

        assertEquals(0, executor.selectExecutions)
        assertFalse(resultSet.closed)
    }

    @Test
    fun `missing execution type fails before JDBC dispatch with no generic fallback`() {
        val script = resultSet(listOf("user_id"), listOf(listOf(7)))
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        val query = select(Output("user_id", null))
        var outcome: ExecuteResult<QueryRow>? = null

        executor.first(query, blockExecute = { outcome = it })

        val failure = assertIs<ExecuteResult.Failure>(outcome)
        assertIs<IllegalArgumentException>(failure.exception)
        assertTrue(failure.exception.message.orEmpty().contains("must declare an execution DataType"))
        assertEquals(0, executor.selectExecutions)
        assertFalse(script.closed)
        assertEquals(0, script.objectReads)
        assertEquals(0, script.typedReads)

        var getOutcome: ExecuteResult<List<QueryRow>>? = null
        executor.get(query, blockExecute = { getOutcome = it })
        assertIs<IllegalArgumentException>(assertIs<ExecuteResult.Failure>(getOutcome).exception)
        assertEquals(0, executor.selectExecutions)
    }

    @Test
    fun `execution and materialization failures use ExecuteResult Failure`() {
        val query = select(Output("user_id", DataType.LONG))
        val executionFailure = SQLException("scripted execution failure")
        val executionExecutor = RecordingExecutor(ExecuteResult.Failure(executionFailure))
        var executionOutcome: ExecuteResult<QueryRow>? = null

        executionExecutor.first(query, blockExecute = { executionOutcome = it })

        assertSame(executionFailure, assertIs<ExecuteResult.Failure>(executionOutcome).exception)

        val brokenResultSet = resultSet(
            labels = listOf("user_id"),
            rows = listOf(listOf(1)),
            failOnTypedGetter = SQLException("scripted materialization failure"),
        )
        val materializationExecutor = RecordingExecutor(ExecuteResult.Success(brokenResultSet.resultSet))
        var materializationOutcome: ExecuteResult<List<QueryRow>>? = null

        materializationExecutor.get(query, blockExecute = { materializationOutcome = it })

        assertIs<ExecuteResult.Failure>(materializationOutcome)
        assertTrue(brokenResultSet.closed)
    }

    @Test
    fun `result set labels must match the builder output aliases`() {
        val script = resultSet(listOf("physical_id"), listOf(listOf(1)))
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        var outcome: ExecuteResult<QueryRow>? = null

        executor.first(select(Output("user_id", DataType.LONG)), blockExecute = { outcome = it })

        assertIs<ExecuteResult.Failure>(outcome)
        assertTrue(script.closed)
    }

    @Test
    fun `row rejects keys other than the SQL output alias`() {
        val script = resultSet(listOf("user_id"), listOf(listOf(1)))
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        var row: QueryRow? = null

        executor.first(select(Output("user_id", DataType.LONG)), blockExecute = {
            row = assertIs<ExecuteResult.Success<QueryRow>>(it).result
        })

        kotlin.test.assertFailsWith<IllegalArgumentException> { row!!["id"] }
    }

    @Test
    fun `model property access resolves through final select output alias without casting`() {
        val script = resultSet(listOf("userOutId"), listOf(listOf(7)))
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        val query = QueryRenderSelectBuilder().select {
            addColumn {
                column(MaterializedUser::class, MaterializedUser::id)
                alias("userOutId")
                execute(DataType.LONG)
            }
        }
        var row: QueryRow? = null

        executor.first(query, blockExecute = {
            row = assertIs<ExecuteResult.Success<QueryRow>>(it).result
        })

        assertEquals(7L, row!!.getValue(MaterializedUser::id))
        assertIs<Long>(row!!.getValue(MaterializedUser::id))
        assertEquals(7L, row!!.getValue("userOutId"))
        assertEquals(7L, row!!["userOutId"])
        assertFailsWith<IllegalArgumentException> { row!!.getValue("user_id") }
        assertTrue(script.closed)
    }

    @Test
    fun `relation property access resolves through final alias without invoking getter`() {
        val script = resultSet(
            labels = listOf("display_name"),
            rows = listOf(listOf(null)),
        )
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        val relation = MaterializedRelation().queryRelation(Unit)
        val query = QueryRenderSelectBuilder().from(relation).select {
            addColumn {
                relationColumn(MaterializedRelation::fullName)
                alias("display_name")
            }
        }
        var row: QueryRow? = null

        executor.first(query, blockExecute = {
            row = assertIs<ExecuteResult.Success<QueryRow>>(it).result
        })

        assertNull(row!!.getValue(MaterializedRelation::fullName))
        assertNull(row!!.getValue(MaterializedRelation::fullName, "display_name"))
        assertNull(row!!["display_name"])
        assertTrue(script.closed)
    }

    @Test
    fun `duplicate model property requires a valid output alias hint`() {
        val script = resultSet(
            labels = listOf("userId", "userOutId", "user_name"),
            rows = listOf(listOf(7, 8, "Ada")),
        )
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        val query = QueryRenderSelectBuilder().select {
            addColumn {
                column(MaterializedUser::class, MaterializedUser::id)
                alias("userId")
            }
            addColumn {
                column(MaterializedUser::class, MaterializedUser::id)
                alias("userOutId")
            }
            addColumn { column(MaterializedUser::class, MaterializedUser::name) }
        }
        var row: QueryRow? = null

        executor.first(query, blockExecute = {
            row = assertIs<ExecuteResult.Success<QueryRow>>(it).result
        })

        assertFailsWith<IllegalArgumentException> { row!!.getValue(MaterializedUser::id) }
        assertEquals(7, row!!.getValue(MaterializedUser::id, "userId"))
        assertEquals(8, row!!.getValue(MaterializedUser::id, "userOutId"))
        assertFailsWith<IllegalArgumentException> { row!!.getValue(MaterializedUser::id, " ") }
        assertFailsWith<IllegalArgumentException> { row!!.getValue(MaterializedUser::id, "missing") }
        assertFailsWith<IllegalArgumentException> { row!!.getValue(MaterializedUser::id, "user_name") }
        assertFailsWith<IllegalArgumentException> { row!!.getValue(OtherMaterializedUser::id) }
    }

    @Test
    fun `property resolution uses owner identity instead of property name`() {
        val script = resultSet(
            labels = listOf("user_id", "other_user_id"),
            rows = listOf(listOf(7, 9)),
        )
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        val query = QueryRenderSelectBuilder().select {
            addColumn { column(MaterializedUser::class, MaterializedUser::id) }
            addColumn { column(OtherMaterializedUser::class, OtherMaterializedUser::id) }
        }
        var row: QueryRow? = null

        executor.first(query, blockExecute = {
            row = assertIs<ExecuteResult.Success<QueryRow>>(it).result
        })

        assertEquals(7, row!!.getValue(MaterializedUser::id))
        assertEquals(9, row!!.getValue(OtherMaterializedUser::id))
    }

    @Test
    fun `unsupported model type fails before JDBC unless explicitly typed`() {
        val script = resultSet(listOf("enabled"), listOf(listOf(true)))
        val executor = RecordingExecutor(ExecuteResult.Success(script.resultSet))
        val unsupportedQuery = QueryRenderSelectBuilder().select {
            addColumn { column(UnsupportedMaterializedUser::class, UnsupportedMaterializedUser::enabled) }
        }
        var unsupportedResult: ExecuteResult<QueryRow>? = null

        executor.first(unsupportedQuery, blockExecute = { unsupportedResult = it })

        val failure = assertIs<ExecuteResult.Failure>(unsupportedResult)
        assertIs<IllegalArgumentException>(failure.exception)
        assertEquals(0, executor.selectExecutions)

        val explicitlyTypedQuery = QueryRenderSelectBuilder().select {
            addColumn {
                column(UnsupportedMaterializedUser::class, UnsupportedMaterializedUser::enabled)
                execute(DataType.INT)
            }
        }
        executor.first(explicitlyTypedQuery, blockExecute = { result ->
            assertIs<ExecuteResult.Success<QueryRow>>(result)
        })
        assertEquals(1, executor.selectExecutions)
    }

    @Test
    fun `sql renders without substituting or changing bound parameters`() {
        val parameters = mutableListOf<SqlParameter<*>>()
        val query = QueryRenderSelectBuilder(params = parameters).select {
            addColumn {
                column { tableColumn("u", "id") }
                alias("user_id")
                execute(DataType.LONG)
            }
        }.where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    sideValue("id", 931)
                }
            }
        }

        val rendered = QueryBuilderExecutor().sql(query)

        assertTrue(rendered.contains("user_id"))
        assertTrue(rendered.contains(":id"))
        assertFalse(rendered.contains("931"))
        assertFalse(rendered.contains("LONG"))
        assertSame(parameters, query.params)
        assertEquals(931, query.params.single().value)
    }

    @Test
    fun `first and get preserve bound parameter identity and ordering`() {
        val firstParameter = SqlParameter.of("first", 11)
        val secondParameter = SqlParameter.of("second", 22)
        val parameters = mutableListOf<SqlParameter<*>>(firstParameter, secondParameter)
        val query = QueryRenderSelectBuilder(params = parameters).select {
                addColumn {
                    column { tableColumn("users", "id") }
                    alias("user_id")
                    execute(DataType.LONG)
                }
        }

        RecordingExecutor(
            ExecuteResult.Success(resultSet(listOf("user_id"), listOf(listOf(11))).resultSet),
        ).first(query, blockExecute = { assertIs<ExecuteResult.Success<QueryRow>>(it) })
        RecordingExecutor(
            ExecuteResult.Success(resultSet(listOf("user_id"), listOf(listOf(11))).resultSet),
        ).get(query, blockExecute = { assertIs<ExecuteResult.Success<List<QueryRow>>>(it) })

        assertSame(parameters, query.params)
        assertEquals(listOf("first", "second"), query.params.map { it.name })
        assertSame(firstParameter, query.params[0])
        assertSame(secondParameter, query.params[1])
    }

    private data class Output(val alias: String?, val dataType: DataType?)

    private fun select(vararg outputs: Output): IQueryRenderSelectApi = QueryRenderSelectBuilder().select {
        outputs.forEachIndexed { index, output ->
            addColumn {
                column { tableColumn("users", "column_$index") }
                if (output.alias != null) alias(output.alias)
                output.dataType?.let { execute(it) }
            }
        }
    }

    private class RecordingExecutor(
        private val selectResult: ExecuteResult<ResultSet>,
    ) : IQueryBuilderExecutor {
        var selectExecutions: Int = 0
            private set

        override fun execute(
            queryBuilder: IQueryRenderSelectApi,
            blockExecute: (ExecuteResult<ResultSet>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) {
            selectExecutions++
            blockQueryInfo?.invoke("SELECT scripted", mutableMapOf("id" to 7))
            blockExecute(selectResult)
        }

        override fun execute(
            queryBuilder: IQueryRenderInsertApi,
            blockExecute: (ExecuteResult<Long>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used by this test")

        override fun execute(
            queryBuilder: IQueryRenderUpdateApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used by this test")

        override fun execute(
            queryBuilder: IQueryRenderDeleteApi,
            blockExecute: (ExecuteResult<Int>) -> Unit,
            blockQueryInfo: ((query: String?, paramsMap: MutableMap<String, Any?>) -> Unit)?,
        ) = error("Not used by this test")
    }

    private data class ResultSetScript(
        val resultSet: ResultSet,
        var closed: Boolean = false,
        var rowsRead: Int = 0,
        var objectReads: Int = 0,
        var typedReads: Int = 0,
    )

    private fun resultSet(
        labels: List<String>,
        rows: List<List<Any?>>,
        failOnTypedGetter: SQLException? = null,
    ): ResultSetScript {
        var rowIndex = -1
        var lastValueWasNull = false
        lateinit var state: ResultSetScript
        val scriptedResultSet = proxy(ResultSet::class.java) { _, method, arguments ->
            when (method.name) {
                "next" -> {
                    rowIndex++
                    val hasNext = rowIndex < rows.size
                    if (hasNext) state.rowsRead++
                    hasNext
                }
                "getMetaData" -> proxy(ResultSetMetaData::class.java) { _, metadataMethod, metadataArguments ->
                    when (metadataMethod.name) {
                        "getColumnCount" -> labels.size
                        "getColumnLabel", "getColumnName" -> labels[(metadataArguments!![0] as Int) - 1]
                        "isNullable" -> ResultSetMetaData.columnNullableUnknown
                        "toString" -> "ScriptedResultSetMetaData"
                        else -> error("Unexpected metadata call: ${metadataMethod.name}")
                    }
                }
                "getLong" -> {
                    failOnTypedGetter?.let { throw it }
                    state.typedReads++
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastValueWasNull = value == null
                    (value as? Number)?.toLong() ?: 0L
                }
                "getInt" -> {
                    failOnTypedGetter?.let { throw it }
                    state.typedReads++
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastValueWasNull = value == null
                    (value as? Number)?.toInt() ?: 0
                }
                "getString" -> {
                    failOnTypedGetter?.let { throw it }
                    state.typedReads++
                    val value = rows[rowIndex][(arguments!![0] as Int) - 1]
                    lastValueWasNull = value == null
                    value as String?
                }
                "wasNull" -> lastValueWasNull
                "getObject" -> {
                    state.objectReads++
                    rows[rowIndex][(arguments!![0] as Int) - 1]
                }
                "close" -> { state.closed = true; null }
                "isClosed" -> state.closed
                "toString" -> "ScriptedResultSet"
                "hashCode" -> System.identityHashCode(state)
                "equals" -> state.resultSet === arguments?.get(0)
                else -> error("Unexpected ResultSet call: ${method.name}")
            }
        }
        state = ResultSetScript(resultSet = scriptedResultSet)
        return state
    }

    private fun <T> proxy(type: Class<T>, handler: InvocationHandler): T =
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler))
}
