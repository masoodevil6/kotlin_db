package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import java.lang.reflect.Proxy
import java.sql.PreparedStatement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class ScopedValue(val value: Int)

private class ParameterizedInlineDeclaration(
    override val params: ScopedValue,
    private val cteName: String,
    private val parameterName: String,
    private val nested: ParameterizedInlineDeclaration? = null,
) : ICteDeclaration<ScopedValue> {
    override fun createCte(params: ScopedValue): Cte = cte(cteName) {
        val sourceAlias = if (nested == null) "leaf_source" else "nested_source"

        if (nested == null) {
            from { table("leaf_table").alias(sourceAlias) }
        } else {
            from { cte(nested).alias(sourceAlias) }
        }

        select {
            addColumn {
                column {
                    if (nested == null) tableAttribute("1")
                    else tableColumn(sourceAlias, "value")
                }
                alias("value")
            }
        }

        where {
            conditions {
                addCondition {
                    logicalAnd()
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValue(parameterName, params.value)
                }
            }
        }
    }
}

private data class JdbcBinding(val index: Int, val value: Any?, val sqlType: Int)

class InlineCteParameterBindingTest {
    @Test
    fun `inline nested declarations bind by SQL order regardless of outer clause call order`() {
        val whereBeforeFrom = buildThreeLevelQuery(
            whereBeforeFrom = true,
            innerParameterName = "inner_value",
            parentParameterName = "parent_value",
            outerParameterName = "outer_value",
        )
        val fromBeforeWhere = buildThreeLevelQuery(
            whereBeforeFrom = false,
            innerParameterName = "inner_value",
            parentParameterName = "parent_value",
            outerParameterName = "outer_value",
        )

        val dialect = MySqlDialect()
        val whereFirstSql = dialect.render(whereBeforeFrom.ast)!!
        val fromFirstSql = dialect.render(fromBeforeWhere.ast)!!
        val (whereFirstTrackedSql, whereFirstOccurrences) = dialect.renderWithBindings(whereBeforeFrom.ast)
        val (fromFirstTrackedSql, fromFirstOccurrences) = dialect.renderWithBindings(fromBeforeWhere.ast)
        val expectedPlaceholderOrder = listOf("inner_value", "parent_value", "outer_value")

        assertEquals(fromFirstSql, whereFirstSql)
        assertEquals(whereFirstSql, whereFirstTrackedSql)
        assertEquals(fromFirstSql, fromFirstTrackedSql)
        assertTrue(whereFirstSql.contains("As outer_source"), whereFirstSql)
        assertTrue(Regex("FROM\\s+\\(\\s*SELECT", RegexOption.IGNORE_CASE).containsMatchIn(whereFirstSql), whereFirstSql)
        assertEquals(expectedPlaceholderOrder, placeholderNames(whereFirstSql))

        val whereFirstParent = whereBeforeFrom.ast.table!!.derivedQuery!!
        val whereFirstNested = whereFirstParent.table!!.derivedQuery!!
        val expectedWhereFirstReferences = listOf(
            conditionParameter(whereFirstNested),
            conditionParameter(whereFirstParent),
            conditionParameter(whereBeforeFrom.ast),
        )
        assertEquals(listOf(11, 22, 33), whereFirstOccurrences.map { assertIs<SqlParameter<*>>(it).value })
        expectedWhereFirstReferences.indices.forEach { index ->
            assertSame(expectedWhereFirstReferences[index], whereFirstOccurrences[index])
        }

        val fromFirstParent = fromBeforeWhere.ast.table!!.derivedQuery!!
        val fromFirstNested = fromFirstParent.table!!.derivedQuery!!
        val expectedFromFirstReferences = listOf(
            conditionParameter(fromFirstNested),
            conditionParameter(fromFirstParent),
            conditionParameter(fromBeforeWhere.ast),
        )
        assertEquals(listOf(11, 22, 33), fromFirstOccurrences.map { assertIs<SqlParameter<*>>(it).value })
        expectedFromFirstReferences.indices.forEach { index ->
            assertSame(expectedFromFirstReferences[index], fromFirstOccurrences[index])
        }

        // The Builder list reflects invocation order; JDBC binding must reflect SQL order.
        assertEquals(
            listOf("outer_value", "inner_value", "parent_value"),
            whereBeforeFrom.params.map { it.name },
        )
        assertEquals(
            listOf("inner_value", "parent_value", "outer_value"),
            fromBeforeWhere.params.map { it.name },
        )

        val expectedBindings = listOf(
            JdbcBinding(1, 11, Types.INTEGER),
            JdbcBinding(2, 22, Types.INTEGER),
            JdbcBinding(3, 33, Types.INTEGER),
        )
        assertEquals(expectedBindings, bindInSqlOrder(whereFirstSql, whereBeforeFrom.params))
        assertEquals(expectedBindings, bindInSqlOrder(fromFirstSql, fromBeforeWhere.params))
    }

    @Test
    fun `same parameter name with independent nested values is rejected instead of being resolved per scope`() {
        val query = buildThreeLevelQuery(
            whereBeforeFrom = true,
            innerParameterName = "shared_value",
            parentParameterName = "shared_value",
            outerParameterName = "shared_value",
        )
        val sql = MySqlDialect().render(query.ast)!!
        val placeholders = placeholderNames(sql)

        assertEquals(listOf("shared_value", "shared_value", "shared_value"), placeholders)
        assertEquals(listOf(33, 11, 22), query.params.map { it.value })

        val builtQuery = BuiltQuery(sql, query.params.toMutableList())
        assertTrue(
            assertFailsWith<IllegalArgumentException> { builtQuery.getListParamNames() }
                .message.orEmpty().contains("Conflicting values supplied for parameter 'shared_value'"),
        )

        val boundCalls = mutableListOf<JdbcBinding>()
        val statement = recordingPreparedStatement(boundCalls)
        assertTrue(
            assertFailsWith<IllegalArgumentException> {
                QueryExecute().readyParamsInQuery(statement, builtQuery.params, placeholders)
            }.message.orEmpty().contains("Conflicting values supplied for parameter 'shared_value'"),
        )
        assertTrue(boundCalls.isEmpty(), "Conflicting same-name values must fail before any JDBC bind")
    }

    private fun buildThreeLevelQuery(
        whereBeforeFrom: Boolean,
        innerParameterName: String,
        parentParameterName: String,
        outerParameterName: String,
    ): IQueryRenderSelectApi {
        val inner = ParameterizedInlineDeclaration(
            params = ScopedValue(11),
            cteName = "inner_query",
            parameterName = innerParameterName,
        )
        val parent = ParameterizedInlineDeclaration(
            params = ScopedValue(22),
            cteName = "parent_query",
            parameterName = parentParameterName,
            nested = inner,
        )

        val query = QueryRenderSelectBuilder()
        val whereBlock: IQueryRenderSelectApi.() -> Unit = {
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValue(outerParameterName, 33)
                    }
                }
            }
        }
        val fromBlock: IQueryRenderSelectApi.() -> Unit = {
            from {
                cte(parent)
                alias("outer_source")
            }
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
                alias("result")
            }
        }
        return query
    }

    private fun placeholderNames(sql: String): List<String> =
        Regex(":([A-Za-z_][A-Za-z0-9_]*)")
            .findAll(sql)
            .map { it.groupValues[1] }
            .toList()

    private fun conditionParameter(ast: IQueryRenderSelectAst): SqlParameter<*> =
        assertIs<SqlParameter<*>>(
            (ast.where!!.condition!!.conditions.single() as IQueryConditionsAst).conditionSideRight,
        )

    private fun bindInSqlOrder(
        sql: String,
        params: MutableList<gog.my_project.data_base.core.query.reader.SqlParameter<*>>,
    ): List<JdbcBinding> {
        val builtQuery = BuiltQuery(sql, params.toMutableList())
        val bindings = mutableListOf<JdbcBinding>()
        QueryExecute().readyParamsInQuery(
            recordingPreparedStatement(bindings),
            builtQuery.params,
            builtQuery.getListParamNames(),
        )
        return bindings
    }

    private fun recordingPreparedStatement(bindings: MutableList<JdbcBinding>): PreparedStatement =
        Proxy.newProxyInstance(
            PreparedStatement::class.java.classLoader,
            arrayOf(PreparedStatement::class.java),
        ) { _, method, args ->
            if (method.name == "setObject") {
                val call = args.orEmpty()
                bindings += JdbcBinding(
                    index = call[0] as Int,
                    value = call[1],
                    sqlType = call[2] as Int,
                )
            }
            null
        } as PreparedStatement
}
