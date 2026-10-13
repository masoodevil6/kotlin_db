package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.data_base.DatabaseConfig
import gog.my_project.data_base.core.data_base.DefaultDatabaseConfig
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.manager.execute.tools.ExecuteResult
import gog.my_project.data_base.query.ast.schema.delete_schema.query_render_delete.QueryRenderDeleteAst
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.insert_schema.query_render_insert.QueryRenderInsertAst
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
    fun `mixed DML parameter representation fails before JDBC connection acquisition`() {
        val first = SqlParameter.of("typed_value", 11)
        val insert = QueryRenderInsertBuilder().apply {
            ast = QueryRenderInsertAst().apply {
                table = table("records")
                columns += insertColumn("typed_value", first)
                columns += QueryColumnInsertAst().apply {
                    columnName = "legacy_value"
                    columnTag = "legacy_value"
                }
            }
            params += first
        }

        assertFailsWith<UnsupportedOperationException> {
            QueryBuilderExecutor().execute(insert, {}, null)
        }
    }

    @Test
    fun `direct DML execution binds independent same-name parameters by SQL occurrence order`() {
        withDatabase { script ->
            val first = SqlParameter("shared_value", 11, Types.INTEGER)
            val second = SqlParameter("shared_value", "22", Types.VARCHAR)
            val insert = QueryRenderInsertBuilder().apply {
                ast = QueryRenderInsertAst().apply {
                    table = table("records")
                    columns += insertColumn("first_value", first)
                    columns += insertColumn("second_value", second)
                }
                params += listOf(first, second)
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
            val first = SqlParameter("shared_value", 33, Types.INTEGER)
            val second = SqlParameter("shared_value", "44", Types.VARCHAR)
            val whereParameter = SqlParameter("shared_value", 55, Types.INTEGER)
            val update = QueryRenderUpdateBuilder().apply {
                ast = QueryRenderUpdateAst().apply {
                    table = table("records")
                    columns += updateColumn("first_value", first)
                    columns += updateColumn("second_value", second)
                    where = where(whereParameter)
                }
                params += listOf(first, second, whereParameter)
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
                params += parameter
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
    fun `direct DML keeps legacy column tags on the legacy execution path`() {
        withDatabase { script ->
            val legacyInsert = QueryRenderInsertBuilder().apply {
                ast = QueryRenderInsertAst().apply {
                    table = table("records")
                    columns += QueryColumnInsertAst().apply {
                        columnName = "value"
                        columnTag = "legacy_value"
                    }
                }
                params += SqlParameter.of("legacy_value", 77)
            }

            var result: ExecuteResult<Long>? = null
            var displaySql: String? = null
            QueryBuilderExecutor().execute(legacyInsert, { result = it }) { sql, _ -> displaySql = sql }

            assertIs<ExecuteResult.Success<Long>>(result)
            assertTrue(displaySql.orEmpty().contains(":legacy_value"), displaySql)
            assertEquals(1, script.preparedSql.orEmpty().count { it == '?' })
            assertEquals(listOf(77), script.bindings.map { it[1] })
            assertEquals(9001L, (result as ExecuteResult.Success<Long>).result)
        }
    }

    private fun table(name: String) = QueryTableAst().apply { table = name }

    private fun insertColumn(name: String, parameter: SqlParameter<*>) = QueryColumnInsertAst().apply {
        columnName = name
        columnTag = parameter.name
        this.parameter = parameter
    }

    private fun updateColumn(name: String, parameter: SqlParameter<*>) = QueryColumnUpdateAst().apply {
        columnAlias = "r"
        columnName = name
        columnTag = parameter.name
        this.parameter = parameter
    }

    private fun where(parameter: SqlParameter<*>): QueryWhereAst = QueryWhereAst().apply {
        condition = QueryConditionsGroupsAst().apply {
            conditions += QueryConditionsAst().apply {
                conditionSideLeft = QueryColumnsBaseAst().apply { tableAttribute = "1" }
                conditionOperation = "="
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
