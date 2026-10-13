package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
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
    override val params: ExecutionFilters,
    private val cteName: String,
    private val nested: ExecutionDeclaration? = null,
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
                    sideValue("shared_value", params.value)
                }
            }
        }
    }
}

class IdentityAwareSelectExecutionTest {
    @Test
    fun `nested same-name declaration bindings execute by render order for both clause orders`() {
        val executions = mutableListOf<Pair<String?, List<List<Any?>>>>()
        for (whereBeforeFrom in listOf(true, false)) {
            withDatabase { script ->
                val leaf = ExecutionDeclaration(ExecutionFilters(11), "leaf_cte")
                val parent = ExecutionDeclaration(ExecutionFilters(22), "parent_cte", nested = leaf)
                val query = QueryRenderSelectBuilder()

                val whereBlock: QueryRenderSelectBuilder.() -> Unit = {
                    where {
                        conditions {
                            addCondition {
                                sideSelector { tableAttribute("1") }
                                operationEqual()
                                sideValue("shared_value", 33)
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
                assertEquals(
                    query.params.associate { parameter -> parameter.name to parameter.value },
                    reportedParams,
                )
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
                "executeQuery" -> resultSetProxy(script)
                "close" -> { script.statementClosed = true; null }
                "isClosed" -> script.statementClosed
                "toString" -> "IdentityAwareRecordingPreparedStatement"
                else -> defaultValue(method.returnType)
            }
        }

    private fun resultSetProxy(script: Script): ResultSet =
        proxy(ResultSet::class.java) { _, method, _ ->
            when (method.name) {
                "next" -> false
                "close" -> { script.resultSetClosed = true; null }
                "isClosed" -> script.resultSetClosed
                "toString" -> "IdentityAwareRecordingResultSet"
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
