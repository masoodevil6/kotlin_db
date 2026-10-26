package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete.QueryRenderDeleteAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_group.QueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.schema.select_schema.conditions_item.QueryConditionsAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.column_base.QueryColumnsBaseAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.table.QueryTableAst
import gog.my_project.data_base.query.ast.schema.select_schema_ast.where.QueryWhereAst
import gog.my_project.data_base.query.ast.schema.update_schema.column_update.QueryColumnUpdateAst
import gog.my_project.data_base.query.ast.schema.update_schema.query_render_update.QueryRenderUpdateAst
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
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
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class IdentityAwareDmlExecutionTest {
    @Test
    fun `UPDATE setValue AST definitions bind without legacy params`() {
        withDatabase { script ->
            val update = QueryRenderUpdateBuilder()
                .table { table("records") }
                .addValue {
                    column("first_value")
                    setValue {
                        paramName("first_update_parameter")
                        paramValue(61)
                    }
                }
                .addValue {
                    column("second_value")
                    setValue { paramValue("second") }
                }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableAttribute("1") }
                            operationEqual()
                            sideValueParam {
                                paramName("where_parameter")
                                paramValue(7)
                            }
                        }
                    }
                }

            var result: ExecuteResult<Int>? = null
            var displaySql: String? = null
            QueryBuilderExecutor().execute(update, { result = it }) { sql, _ -> displaySql = sql }

            assertEquals(1, assertIs<ExecuteResult.Success<Int>>(result).result)
            assertTrue(displaySql.orEmpty().contains(":first_update_parameter"), displaySql)
            assertTrue(displaySql.orEmpty().contains(":second_value"), displaySql)
            assertTrue(displaySql.orEmpty().contains(":where_parameter"), displaySql)
            assertTrue(
                displaySql.orEmpty().indexOf(":first_update_parameter") < displaySql.orEmpty().indexOf(":second_value"),
                displaySql,
            )
            assertTrue(
                displaySql.orEmpty().indexOf(":second_value") < displaySql.orEmpty().indexOf(":where_parameter"),
                displaySql,
            )
            assertEquals(listOf(61, "second", 7), script.bindings.map { it[1] })
            assertEquals(listOf(Types.INTEGER, Types.VARCHAR, Types.INTEGER), script.bindings.map { it.last() })
            assertEquals(3, script.preparedSql.orEmpty().count { it == '?' })
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }
    }

    @Test
    fun `INSERT setValue AST definitions bind without legacy params`() {
        withDatabase { script ->
            val insert = QueryRenderInsertBuilder()
                .table { table("records") }
                .addValue {
                    column("first_value")
                    setValue {
                        paramName("first_insert_parameter")
                        paramValue(61)
                    }
                }
                .addValue {
                    column("second_value")
                    setValue {
                        paramName("second_insert_parameter")
                        paramValue("second")
                    }
                }

            var result: ExecuteResult<Long>? = null
            var displaySql: String? = null
            QueryBuilderExecutor().execute(insert, { result = it }) { sql, _ -> displaySql = sql }

            assertEquals(9001L, assertIs<ExecuteResult.Success<Long>>(result).result)
            assertTrue(displaySql.orEmpty().contains(":first_insert_parameter"), displaySql)
            assertTrue(displaySql.orEmpty().contains(":second_insert_parameter"), displaySql)
            assertTrue(
                displaySql.orEmpty().indexOf(":first_insert_parameter") <
                    displaySql.orEmpty().indexOf(":second_insert_parameter"),
                displaySql,
            )
            assertEquals(listOf(61, "second"), script.bindings.map { it[1] })
            assertEquals(listOf(Types.INTEGER, Types.VARCHAR), script.bindings.map { it.last() })
            assertEquals(2, script.preparedSql.orEmpty().count { it == '?' })
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }
    }

    @Test
    fun `DELETE WHERE AST parameter binds without legacy params`() {
        withDatabase { script ->
            val delete = QueryRenderDeleteBuilder()
                .table { table("records") }
                .where {
                    conditions {
                        addCondition {
                            sideSelector { tableAttribute("id") }
                            operationEqual()
                            sideValueParam {
                                paramName("id1")
                                paramValue(5)
                            }
                        }
                    }
                }

            var result: ExecuteResult<Int>? = null
            var displaySql: String? = null
            QueryBuilderExecutor().execute(delete, { result = it }) { sql, _ -> displaySql = sql }

            assertEquals(1, assertIs<ExecuteResult.Success<Int>>(result).result)
            assertTrue(displaySql.orEmpty().contains(":id1"), displaySql)
            assertEquals(listOf(5), script.bindings.map { it[1] })
            assertEquals(listOf(Types.INTEGER), script.bindings.map { it.last() })
            assertEquals(1, script.preparedSql.orEmpty().count { it == '?' })
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }
    }

    @Test
    fun `DML column without parameter AST fails before JDBC connection acquisition`() {
        val insert = QueryRenderInsertBuilder().apply {
            ast = QueryRenderInsertAst().apply {
                table = table("records")
                columns += insertColumn("typed_value", parameterAst("typed_value", 11, Types.INTEGER))
                columns += QueryColumnInsertAst().apply {
                    metadata.columnName = "missing_value"
                    metadata.columnTag = "missing_value"
                }
            }
        }

        assertFailsWith<IllegalStateException> {
            QueryBuilderExecutor().execute(insert, {}, null)
        }
    }

    @Test
    fun `direct DML execution binds independent same-name parameters by SQL occurrence order`() {
        withDatabase { script ->
            val first = parameterAst("shared_value", 11, Types.INTEGER)
            val second = parameterAst("shared_value", "22", Types.VARCHAR)
            val insert = QueryRenderInsertBuilder().apply {
                ast = QueryRenderInsertAst().apply {
                    table = table("records")
                    columns += insertColumn("first_value", first)
                    columns += insertColumn("second_value", second)
                }
            }

            var result: ExecuteResult<Long>? = null
            QueryBuilderExecutor().execute(insert, { result = it }, null)

            assertIs<ExecuteResult.Success<Long>>(result)
            assertEquals(9001L, (result as ExecuteResult.Success<Long>).result)
            assertEquals(listOf(11, "22"), script.bindings.map { it[1] })
            assertEquals(listOf(Types.INTEGER, Types.VARCHAR), script.bindings.map { it.last() })
            assertTrue(script.preparedSql.orEmpty().count { it == '?' } == 2)
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }

        withDatabase { script ->
            val first = parameterAst("shared_value", 33, Types.INTEGER)
            val second = parameterAst("shared_value", "44", Types.VARCHAR)
            val whereParameter = parameterAst("shared_value", 55, Types.INTEGER)
            val update = QueryRenderUpdateBuilder().apply {
                ast = QueryRenderUpdateAst().apply {
                    table = table("records")
                    columns += updateColumn("first_value", first)
                    columns += updateColumn("second_value", second)
                    where = where(whereParameter)
                }
            }

            var result: ExecuteResult<Int>? = null
            QueryBuilderExecutor().execute(update, { result = it }, null)

            assertEquals(1, assertIs<ExecuteResult.Success<Int>>(result).result)
            assertEquals(listOf(33, "44", 55), script.bindings.map { it[1] })
            assertEquals(listOf(Types.INTEGER, Types.VARCHAR, Types.INTEGER), script.bindings.map { it.last() })
            assertEquals(3, script.preparedSql.orEmpty().count { it == '?' })
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }

        withDatabase { script ->
            val parameter = SqlParameter.nullOf("shared_value", Types.INTEGER)
            val delete = QueryRenderDeleteBuilder().apply {
                ast = QueryRenderDeleteAst().apply {
                    table = table("records")
                    where = where(parameter)
                }
            }

            var result: ExecuteResult<Int>? = null
            QueryBuilderExecutor().execute(delete, { result = it }, null)

            assertEquals(1, assertIs<ExecuteResult.Success<Int>>(result).result)
            assertEquals(listOf(listOf<Any?>(1, Types.INTEGER)), script.bindings)
            assertEquals(1, script.preparedSql.orEmpty().count { it == '?' })
            assertTrue(script.statementClosed)
            assertTrue(script.connectionClosed)
        }
    }

    @Test
    fun `direct DML rejects incomplete parameter AST metadata before JDBC`() {
        withDatabase { script ->
            val insert = QueryRenderInsertBuilder().apply {
                ast = QueryRenderInsertAst().apply {
                    table = table("records")
                    columns += QueryColumnInsertAst().apply {
                        metadata.columnName = "typed_value"
                        metadata.columnTag = "typed_value"
                        parameterAst = QueryParameterAst<Any?>().apply {
                            metadata.name = "typed_value"
                            metadata.value = 77
                        }
                    }
                }
            }

            assertFailsWith<IllegalArgumentException> {
                QueryBuilderExecutor().execute(insert, {}, null)
            }
            assertEquals(null, script.preparedSql)
        }
    }

    private fun table(name: String) = QueryTableAst().apply { metadata.nameFromAst = name }

    private fun insertColumn(name: String, parameter: IQueryParameterAst<*>) = QueryColumnInsertAst().apply {
        metadata.columnName = name
        metadata.columnTag = parameter.metadata.name
        parameterAst = parameter
    }

    private fun updateColumn(name: String, parameter: IQueryParameterAst<*>) = QueryColumnUpdateAst().apply {
        metadata.columnAlias = "r"
        metadata.columnName = name
        metadata.columnTag = parameter.metadata.name
        parameterAst = parameter
    }

    private fun parameterAst(name: String, value: Any?, sqlType: Int) = QueryParameterAst<Any?>().apply {
        metadata.name = name
        metadata.value = value
        metadata.sqlType = sqlType
    }

    private fun where(parameter: Any): QueryWhereAst = QueryWhereAst().apply {
        condition = QueryConditionsGroupsAst().apply {
            conditions += QueryConditionsAst().apply {
                conditionSideLeft = QueryColumnsBaseAst().apply { metadata.columnNameFromAst = "1" }
                metadata.conditionOperation = "="
                conditionSideRight = parameter
            }
        }
    }

    private fun <T> withDatabase(block: (Script) -> T): T {
        val previous = DefaultDatabaseConfig.config
        val script = Script()
        val driver = RecordingDriver(script)
        DriverManager.registerDriver(driver)
        DefaultDatabaseConfig.config = DatabaseConfig(
            dbDomain = "jdbc:identity-dml",
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
        var statementClosed = false
        var connectionClosed = false
    }

    private inner class RecordingDriver(private val script: Script) : Driver {
        override fun connect(url: String?, info: Properties?): Connection? =
            if (url != null && acceptsURL(url)) connectionProxy(script) else null

        override fun acceptsURL(url: String?): Boolean = url?.startsWith("jdbc:identity-dml/") == true
        override fun getPropertyInfo(url: String?, info: Properties?) = emptyArray<java.sql.DriverPropertyInfo>()
        override fun getMajorVersion() = 1
        override fun getMinorVersion() = 0
        override fun jdbcCompliant() = false
        override fun getParentLogger(): Logger = Logger.getLogger("identity-dml-test")
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
            else -> defaultValue(method.returnType)
        }
    }

    private fun preparedStatementProxy(script: Script): PreparedStatement =
        proxy(PreparedStatement::class.java) { _, method, args ->
            when (method.name) {
                "setObject", "setNull" -> { script.bindings += args.orEmpty().toList(); null }
                "executeUpdate" -> 1
                "getGeneratedKeys" -> generatedKeysProxy()
                "close" -> { script.statementClosed = true; null }
                "isClosed" -> script.statementClosed
                else -> defaultValue(method.returnType)
            }
        }

    private fun generatedKeysProxy(): ResultSet = proxy(ResultSet::class.java) { _, method, _ ->
        when (method.name) {
            "next" -> true
            "getLong" -> 9001L
            "close" -> null
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
