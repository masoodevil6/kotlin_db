package gog.my_project.data_base.query.executer.manager

import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.query.ast.schema.insert_schema.column.QueryColumnInsertAst
import gog.my_project.data_base.query.ast.schema.parameter.QueryParameterAst
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MutationQueryConsumerTest {
    @Test
    fun `INSERT SQL callback receives AST bindings in column emission order`() {
        val insert = QueryRenderInsertBuilder()
            .table { table("user_users") }
            .addValue {
                column("name")
                setValue {
                    paramName("name")
                    paramValue("Ali")
                }
            }
            .addValue {
                column("family")
                setValue {
                    paramName("family")
                    paramValue("Sadegi")
                }
            }
            .addValue {
                column("age")
                setValue {
                    paramName("age")
                    paramValue(50)
                }
            }

        var callbackSql: String? = null
        var callbackParams = emptyList<IQueryParameterBinding>()
        QueryBuilderExecutor().queryBuilder(insert).sql { (sql, params) ->
            callbackSql = sql
            callbackParams = params.toList()
        }
        val sql = callbackSql
        val params = callbackParams

        assertTrue(sql.orEmpty().contains("values"), sql)
        assertEquals(listOf("name", "family", "age"), params.map { it.name })
        assertEquals(listOf("Ali", "Sadegi", 50), params.map { it.value })
        assertEquals(listOf(Types.VARCHAR, Types.VARCHAR, Types.INTEGER), params.map { it.sqlType })
        params.forEach { assertIs<QueryParameterBinding>(it) }
        assertTrue(sql.orEmpty().indexOf(":name") < sql.orEmpty().indexOf(":family"), sql)
        assertTrue(sql.orEmpty().indexOf(":family") < sql.orEmpty().indexOf(":age"), sql)
    }

    @Test
    fun `UPDATE SQL callback orders SET bindings before WHERE bindings`() {
        val update = QueryRenderUpdateBuilder()
            .table { table("user_users").alias("uu") }
            .addValue {
                column("uu", "name")
                setValue {
                    paramName("name")
                    paramValue("--changed--")
                }
            }
            .where {
                conditions {
                    addCondition {
                        sideSelector { tableColumn("uu", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("id1")
                            paramValue(5)
                        }
                    }
                }
            }

        var callbackSql: String? = null
        var callbackParams = emptyList<IQueryParameterBinding>()
        QueryBuilderExecutor().queryBuilder(update).sql { (sql, params) ->
            callbackSql = sql
            callbackParams = params.toList()
        }
        val sql = callbackSql
        val params = callbackParams

        assertTrue(sql.orEmpty().indexOf(":name") < sql.orEmpty().indexOf(":id1"), sql)
        assertEquals(listOf("name", "id1"), params.map { it.name })
        assertEquals(listOf("--changed--", 5), params.map { it.value })
        assertEquals(listOf(Types.VARCHAR, Types.INTEGER), params.map { it.sqlType })
        params.forEach { assertIs<QueryParameterBinding>(it) }
    }

    @Test
    fun `DELETE SQL callback receives WHERE binding`() {
        val delete = QueryRenderDeleteBuilder()
            .table { table("user_users") }
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

        var callbackSql: String? = null
        var callbackParams = emptyList<IQueryParameterBinding>()
        QueryBuilderExecutor().queryBuilder(delete).sql { (sql, params) ->
            callbackSql = sql
            callbackParams = params.toList()
        }
        val sql = callbackSql
        val params = callbackParams

        assertTrue(sql.orEmpty().contains(":id1"), sql)
        assertEquals(listOf("id1"), params.map { it.name })
        assertEquals(listOf(5), params.map { it.value })
        assertEquals(listOf(Types.INTEGER), params.map { it.sqlType })
        params.forEach { assertIs<QueryParameterBinding>(it) }
    }

    @Test
    fun `reusing one parameter AST returns one binding occurrence per SQL placeholder`() {
        val update = QueryRenderUpdateBuilder()
            .table { table("records") }
            .addValue {
                column("first_value")
                setValue {
                    paramName("shared_value")
                    paramValue(77)
                }
            }
            .addValue {
                column("second_value")
                setValue {
                    paramName("unused_value")
                    paramValue(88)
                }
            }
        update.ast.columns[1].parameterAst = update.ast.columns[0].parameterAst

        var callbackSql: String? = null
        var callbackParams: List<IQueryParameterBinding> = emptyList()
        QueryBuilderExecutor().queryBuilder(update).sql { (sql, params) ->
            callbackSql = sql
            callbackParams = params.toList()
        }

        assertEquals(2, Regex(":shared_value\\b").findAll(callbackSql.orEmpty()).count())
        assertEquals(listOf("shared_value", "shared_value"), callbackParams.map { it.name })
        assertEquals(listOf(77, 77), callbackParams.map { it.value })
        assertSame(callbackParams[0], callbackParams[1])
    }

    @Test
    fun `manually assembled INSERT parameter AST reaches the query consumer`() {
        val parameterAst = QueryParameterAst<Any?>().apply {
            metadata.name = "insert_value"
            metadata.value = "old"
            metadata.sqlType = Types.VARCHAR
        }
        val insert = QueryRenderInsertBuilder().apply {
            table { table("records") }
            ast.columns += QueryColumnInsertAst().apply {
                metadata.columnName = "value"
                metadata.columnTag = "value"
                this.parameterAst = parameterAst
            }
        }

        val built = QueryBuilderExecutor().queryBuilder(insert).sql()

        assertTrue(built.query.orEmpty().contains(":insert_value"), built.query)
        assertEquals(1, built.params.size)
        assertEquals("insert_value", built.params.single().name)
        assertEquals("old", built.params.single().value)
        assertEquals(Types.VARCHAR, built.params.single().sqlType)
    }
}
