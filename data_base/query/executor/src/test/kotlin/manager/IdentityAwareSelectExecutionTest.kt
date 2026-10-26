package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.enums.DataType
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.cte.declaration.CteDeclaration
import gog.my_project.data_base.query.executer.interfaces.IQueryBuilderExecutor
import gog.my_project.data_base.query.executer.result.QueryRow
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import gog.my_project.data_base.query.meta_data.analysis.EffectiveQueryMetadataAnalyzer
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types
import java.util.Properties
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

private data class ExecutionFilters(val value: Int)

private class ExecutionDeclaration(
    private val cteName: String,
    private val nested: Cte? = null,
) : ICteDeclaration<ExecutionFilters> {
    override fun createCte(params: ExecutionFilters): Cte = cte(cteName) {
        val sourceAlias = if (nested == null) "leaf_source" else "nested_source"
        if (nested == null) {
            from { table("leaf_rows").alias(sourceAlias) }
        } else {
            from { cte(nested).alias(sourceAlias) }
        }
        select {
            addColumn {
                column {
                    if (nested == null) tableAttribute("1") else tableColumn(sourceAlias, "value")
                }
                alias("value")
            }
        }
        where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValueParam {
                        paramName("shared_value")
                        paramValue(params.value)
                    }
                }
            }
        }
    }
}

@QBQuery(name = "leaf_execute_cte")
private class LeafExecuteCte : CteDeclaration<Int, LeafExecuteCte>() {
    override fun fromQueryRow(row: QueryRow): LeafExecuteCte =
        error("Identity-aware raw execution fixture does not map rows")

    @QBQuerySelect(name = "value", alias = "leaf_value")
    val value: Int get() = error("Executing a CTE must not read output getters")

    override fun createCte(params: Int): Cte = buildCte {
        select {
            addColumn {
                column { tableAttribute("1") }
                alias(LeafExecuteCte::value)
                execute(DataType.INT)
            }
        }
        where {
            conditions {
                addCondition {
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValueParam {
                        paramName("leaf_filter")
                        paramValue(params)
                    }
                }
            }
        }
    }
}

@QBQuery(name = "parent_execute_cte")
private class ParentExecuteCte : CteDeclaration<Int, ParentExecuteCte>() {
    override fun fromQueryRow(row: QueryRow): ParentExecuteCte =
        error("Identity-aware raw execution fixture does not map rows")

    @QBQuerySelect(name = "value", alias = "parent_value")
    val value: Int get() = error("Executing a CTE must not read output getters")

    override fun createCte(params: Int): Cte {
        val dependency = LeafExecuteCte().createCte(11)
        return buildCte {
            withs { addWith { with(dependency) } }
            from { cte(dependency.name).alias("leaf") }
            select {
                addColumn {
                    column {
                        tableSelector("leaf")
                        columnSelector(LeafExecuteCte::value)
                    }
                    alias(ParentExecuteCte::value)
                    execute(DataType.INT)
                }
            }
            where {
                conditions {
                    addCondition {
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam {
                            paramName("parent_filter")
                            paramValue(params)
                        }
                    }
                }
            }
        }
    }

    fun executeRawForTest(
        queryManager: IQueryBuilderExecutor,
        params: Int,
    ): ExecuteResult<List<QueryRow>> =
        configureConsumer(queryReady(queryManager, params)).execute()
}

private class UntypedExecuteCte : CteDeclaration<Unit, UntypedExecuteCte>() {
    override fun fromQueryRow(row: QueryRow): UntypedExecuteCte =
        error("Untyped raw execution fixture does not map rows")

    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("untyped_value") } }
    }
}

@QBTable(name = "computed_users", alias = "cu")
private class ComputedUserModel : IModelBase {
    @QBColumn(name = "id")
    val id: Int get() = error("Model metadata must not read source properties")

    @QBColumn(name = "first_name")
    val firstName: String get() = error("Model metadata must not read source properties")

    @QBColumn(name = "family")
    val family: String get() = error("Model metadata must not read source properties")
}

@QBQuery(name = "computed_output_execute_cte")
private class ComputedOutputExecuteCte : CteDeclaration<Unit, ComputedOutputExecuteCte>() {
    override fun fromQueryRow(row: QueryRow): ComputedOutputExecuteCte =
        error("Identity-aware raw execution fixture does not map rows")

    fun executeRawForTest(
        queryManager: IQueryBuilderExecutor,
        params: Unit,
    ): ExecuteResult<List<QueryRow>> =
        configureConsumer(queryReady(queryManager, params)).execute()

    @QBQuerySelect(name = "full_name", alias = "fullName")
    val fullName: String? get() = error("Typed output property must not be read")

    @QBQuerySelect(name = "id", alias = "id")
    val id: Int get() = error("Typed output property must not be read")

    @QBQuerySelect(name = "record_count", alias = "recordCount")
    val recordCount: Long get() = error("Typed output property must not be read")

    override fun createCte(params: Unit): Cte = buildCte {
        from { model(ComputedUserModel::class) }
        select {
            addColumn {
                methodConcat {
                    column {
                        tableSelector(ComputedUserModel::class)
                        columnSelector(ComputedUserModel::firstName)
                    }
                    string("-")
                    column {
                        tableSelector(ComputedUserModel::class)
                        columnSelector(ComputedUserModel::family)
                    }
                }
                alias(ComputedOutputExecuteCte::fullName)
                execute(DataType.STRING)
            }
            addColumn {
                column {
                    tableSelector(ComputedUserModel::class)
                    columnSelector(ComputedUserModel::id)
                }
                alias(ComputedOutputExecuteCte::id)
                execute(DataType.INT)
            }
            addColumn {
                methodCount {
                    addColumn {
                        tableSelector(ComputedUserModel::class)
                        columnSelector(ComputedUserModel::id)
                    }
                }
                alias(ComputedOutputExecuteCte::recordCount)
                execute(DataType.LONG)
            }
        }
    }
}

class IdentityAwareSelectExecutionTest {
    @Test
    fun `raw consumer executes declaration dependency CTE with parameters through executor`() {
        withDatabase { script ->
            script.resultValues = mapOf("parent_value" to 41)

            val result: ExecuteResult<List<QueryRow>> =
                ParentExecuteCte().executeRawForTest(QueryBuilderExecutor(), 22)

            val rows = assertIs<ExecuteResult.Success<List<QueryRow>>>(result, result.toString()).result.orEmpty()
            assertEquals(41, rows.single().getValue(ParentExecuteCte::value))
            assertTrue(script.preparedSql.orEmpty().trimStart().startsWith("WITH", ignoreCase = true), script.preparedSql)
            assertTrue(script.preparedSql.orEmpty().contains("leaf_execute_cte"), script.preparedSql)
            assertEquals(
                listOf<List<Any?>>(
                    listOf(1, 11, Types.INTEGER),
                    listOf(2, 22, Types.INTEGER),
                ),
                script.bindings,
            )
            assertTrue(script.resultSetClosed)
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }
    }

    @Test
    fun `computed output aliases are retained in raw query rows`() {
        withDatabase { script ->
            script.resultValues = linkedMapOf(
                "fullName" to "Ada-Lovelace",
                "id" to 42,
                "recordCount" to 3L,
            )

            val declaration = ComputedOutputExecuteCte()
            val definition = declaration.createCte(Unit).definition.ast
            val outputs = definition.select!!.columns
            val fullNameOutput = outputs[0]
            assertEquals("full_name", fullNameOutput.metadata.logicalOutputName)
            assertEquals("fullName", fullNameOutput.metadata.ColumnAlias)
            assertEquals(DataType.STRING, fullNameOutput.metadata.ExecutionType)
            assertEquals(ComputedOutputExecuteCte::fullName, fullNameOutput.metadata.PropertyReference)
            assertEquals(ComputedOutputExecuteCte::id, outputs[1].metadata.PropertyReference)
            assertEquals(DataType.INT, outputs[1].metadata.ExecutionType)
            assertEquals(ComputedOutputExecuteCte::recordCount, outputs[2].metadata.PropertyReference)
            assertEquals(DataType.LONG, outputs[2].metadata.ExecutionType)
            val effectiveFullName = EffectiveQueryMetadataAnalyzer().analyze(definition).outputs[0]
            assertEquals("full_name", effectiveFullName.logicalOutputName)
            assertEquals("fullName", effectiveFullName.publishedSqlAlias)
            assertEquals(DataType.STRING, effectiveFullName.executionType)

            val result: ExecuteResult<List<QueryRow>> =
                declaration.executeRawForTest(QueryBuilderExecutor(), Unit)
            val rows = assertIs<ExecuteResult.Success<List<QueryRow>>>(result, result.toString()).result.orEmpty()
            val row = rows.single()
            assertEquals("Ada-Lovelace", row.getValue(ComputedOutputExecuteCte::fullName))
            assertEquals(42, row.getValue(ComputedOutputExecuteCte::id))
            assertEquals(3L, row.getValue(ComputedOutputExecuteCte::recordCount))
            assertEquals("Ada-Lovelace", row.getValue("fullName"))

            assertTrue(script.preparedSql.orEmpty().contains("concat", ignoreCase = true), script.preparedSql)
            assertTrue(script.preparedSql.orEmpty().contains("cu.first_name"), script.preparedSql)
            assertTrue(script.preparedSql.orEmpty().contains("cu.family"), script.preparedSql)
            assertTrue(script.preparedSql.orEmpty().contains("As fullName"), script.preparedSql)
            assertTrue(script.preparedSql.orEmpty().contains("count", ignoreCase = true), script.preparedSql)
        }
    }

    @Test
    fun `declaration preserves executor failure for outputs without execution type`() {
        val result = UntypedExecuteCte().execute(QueryBuilderExecutor(), Unit)
        val failure = assertIs<ExecuteResult.Failure>(result)
        assertTrue(failure.exception.message.orEmpty().contains("execution DataType"))
    }

    @Test
    fun `declaration returns database execution failures unchanged`() {
        withDatabase { script ->
            script.executeFailure = SQLException("scripted CTE execution failure")

            val result = ParentExecuteCte().execute(QueryBuilderExecutor(), 22)

            val failure = assertIs<ExecuteResult.Failure>(result)
            assertEquals("scripted CTE execution failure", failure.exception.message)
        }
    }

    @Test
    fun `independent scalar and collection AST parameters bind and report in SQL order`() {
        withDatabase { script ->
            val query = QueryRenderSelectBuilder()
            query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
            query.from { table("users").alias("u") }
            query.where {
                conditions {
                    addCondition {
                        sideSelector { tableColumn("u", "id") }
                        operationIn()
                        sideValueCollection {
                            addParam {
                                paramName("id1")
                                paramValue(11)
                            }
                            addParam {
                                paramName("id2")
                                paramValue(22)
                            }
                        }
                    }
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationGreaterThan()
                        sideValueParam {
                            paramName("minimum_id")
                            paramValue(33)
                        }
                    }
                }
            }

            val expected = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.render(query.ast, MySqlDialect()),
            )
            assertEquals(listOf("id1", "id2", "minimum_id"), expected.parameterOccurrences.map { it.name })
            assertEquals(listOf(11, 22, 33), expected.parameterOccurrences.map { it.value })
            assertEquals(3, expected.parameterOccurrences.count { it is QueryParameterBinding })
            var callbackCount = 0
            var queryInfo: Pair<String?, Map<String, Any?>>? = null
            QueryBuilderExecutor().execute(
                queryBuilder = query,
                blockExecute = { result ->
                    callbackCount++
                    assertIs<ExecuteResult.Success<ResultSet>>(result).result?.close()
                },
                blockQueryInfo = { sql, params -> queryInfo = sql to params.toMap() },
            )

            assertEquals(1, callbackCount)
            val (reportedSql, reportedParams) = assertNotNull(queryInfo)
            assertTrue(reportedSql.orEmpty().contains(":id1"), reportedSql)
            assertTrue(reportedSql.orEmpty().contains(":id2"), reportedSql)
            assertTrue(reportedSql.orEmpty().contains(":minimum_id"), reportedSql)
            assertEquals(mapOf("id1" to 11, "id2" to 22, "minimum_id" to 33), reportedParams)
            assertEquals(expected.jdbcSql, script.preparedSql)
            assertEquals(
                listOf<List<Any?>>(
                    listOf(1, 11, Types.INTEGER),
                    listOf(2, 22, Types.INTEGER),
                    listOf(3, 33, Types.INTEGER),
                ),
                script.bindings,
            )
            assertTrue(script.resultSetClosed)
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }
    }

    @Test
    fun `nested same-name declaration bindings execute by render order for both clause orders`() {
        val executions = mutableListOf<Pair<String?, List<List<Any?>>>>()
        for (whereBeforeFrom in listOf(true, false)) {
            withDatabase { script ->
                val leaf = ExecutionDeclaration("leaf_cte").createCte(ExecutionFilters(11))
                val parent = ExecutionDeclaration("parent_cte", nested = leaf).createCte(ExecutionFilters(22))
                val query = QueryRenderSelectBuilder()

                val whereBlock: QueryRenderSelectBuilder.() -> Unit = {
                    where {
                        conditions {
                            addCondition {
                                sideSelector { tableAttribute("1") }
                                operationEqual()
                                sideValueParam {
                                    paramName("shared_value")
                                    paramValue(33)
                                }
                            }
                        }
                    }
                }
                val fromBlock: QueryRenderSelectBuilder.() -> Unit = {
                    from { cte(parent).alias("outer_source") }
                }
                if (whereBeforeFrom) {
                    query.whereBlock()
                    query.fromBlock()
                } else {
                    query.fromBlock()
                    query.whereBlock()
                }
                query.select {
                    addColumn {
                        column { tableColumn("outer_source", "value") }
                        alias("value")
                    }
                }

                val expected = assertIs<IdentityAwareQueryRenderResult>(
                    QueryParameterRendering.render(query.ast, MySqlDialect()),
                )
                assertEquals(listOf(11, 22, 33), expected.parameterOccurrences.map { it.value })

                var callbackCount = 0
                var queryInfo: Pair<String?, Map<String, Any?>>? = null
                QueryBuilderExecutor().execute(
                    queryBuilder = query,
                    blockExecute = { result ->
                        callbackCount++
                        assertIs<ExecuteResult.Success<ResultSet>>(result).result?.close()
                    },
                    blockQueryInfo = { sql, params -> queryInfo = sql to params.toMap() },
                )

                assertEquals(1, callbackCount)
                val (reportedSql, reportedParams) = assertNotNull(queryInfo)
                assertTrue(reportedSql.orEmpty().contains(":shared_value"), reportedSql)
                assertEquals(mapOf("shared_value" to 11), reportedParams)
                assertEquals(expected.jdbcSql, script.preparedSql)
                assertEquals(
                    listOf<List<Any?>>(
                        listOf(1, 11, Types.INTEGER),
                        listOf(2, 22, Types.INTEGER),
                        listOf(3, 33, Types.INTEGER),
                    ),
                    script.bindings,
                )
                assertEquals(true, script.resultSetClosed)
                assertEquals(true, script.statementClosed)
                assertEquals(true, script.connectionClosed)
                executions += script.preparedSql to script.bindings.toList()
            }
        }
        assertEquals(executions[0], executions[1])
    }

    private fun <T> withDatabase(block: (Script) -> T): T {
        val previous = DefaultDatabaseConfig.config
        val script = Script()
        val driver = RecordingDriver(script)
        DriverManager.registerDriver(driver)
        DefaultDatabaseConfig.config = DatabaseConfig(
            dbDomain = "jdbc:identity-query-execution",
            dbPort = null,
            dbName = "test",
            dbUserName = "test",
            dbPassword = "",
        )
        return try {
            block(script)
        } finally {
            DefaultDatabaseConfig.config = previous
            DriverManager.deregisterDriver(driver)
        }
    }

    private class Script {
        var preparedSql: String? = null
        val bindings = mutableListOf<List<Any?>>()
        var resultSetClosed = false
        var statementClosed = false
        var connectionClosed = false
        var resultValues: Map<String, Any?>? = null
        var resultConsumed = false
        var executeFailure: SQLException? = null
    }

    private inner class RecordingDriver(private val script: Script) : Driver {
        override fun connect(url: String?, info: Properties?): Connection? {
            if (url == null || !acceptsURL(url)) return null
            return connectionProxy(script)
        }

        override fun acceptsURL(url: String?): Boolean =
            url?.startsWith("jdbc:identity-query-execution/") == true

        override fun getPropertyInfo(url: String?, info: Properties?) = emptyArray<java.sql.DriverPropertyInfo>()
        override fun getMajorVersion() = 1
        override fun getMinorVersion() = 0
        override fun jdbcCompliant() = false
        override fun getParentLogger(): Logger = Logger.getLogger("identity-query-execution-test")
    }

    private fun connectionProxy(script: Script): Connection = proxy(Connection::class.java) { _, method, args ->
        when (method.name) {
            "prepareStatement" -> {
                script.preparedSql = args?.get(0) as String
                preparedStatementProxy(script)
            }
            "close" -> { script.connectionClosed = true; null }
            "isClosed" -> script.connectionClosed
            "isValid" -> true
            "toString" -> "IdentityAwareRecordingConnection"
            else -> defaultValue(method.returnType)
        }
    }

    private fun preparedStatementProxy(script: Script): PreparedStatement =
        proxy(PreparedStatement::class.java) { _, method, args ->
            when (method.name) {
                "setObject", "setNull" -> { script.bindings += args.orEmpty().toList(); null }
                "executeQuery" -> {
                    script.executeFailure?.let { throw it }
                    resultSetProxy(script)
                }
                "close" -> { script.statementClosed = true; null }
                "isClosed" -> script.statementClosed
                "toString" -> "IdentityAwareRecordingPreparedStatement"
                else -> defaultValue(method.returnType)
            }
        }

    private fun resultSetProxy(script: Script): ResultSet =
        proxy(ResultSet::class.java) { _, method, args ->
            when (method.name) {
                "next" -> {
                    if (script.resultValues != null && !script.resultConsumed) {
                        script.resultConsumed = true
                        true
                    } else {
                        false
                    }
                }
                "getMetaData" -> resultSetMetaDataProxy(script)
                "getInt", "getLong", "getString" -> {
                    val index = (args?.get(0) as? Int)?.minus(1)
                    index?.let { script.resultValues?.values?.elementAtOrNull(it) }
                }
                "wasNull" -> false
                "close" -> { script.resultSetClosed = true; null }
                "isClosed" -> script.resultSetClosed
                "toString" -> "IdentityAwareRecordingResultSet"
                else -> defaultValue(method.returnType)
            }
        }

    private fun resultSetMetaDataProxy(script: Script): ResultSetMetaData =
        proxy(ResultSetMetaData::class.java) { _, method, args ->
            when (method.name) {
                "getColumnCount" -> script.resultValues?.size ?: 0
                "getColumnLabel", "getColumnName" -> {
                    val index = (args?.get(0) as? Int)?.minus(1)
                    index?.let { script.resultValues?.keys?.elementAtOrNull(it) }.orEmpty()
                }
                else -> defaultValue(method.returnType)
            }
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>, handler: InvocationHandler): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type), handler) as T

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0.0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}
