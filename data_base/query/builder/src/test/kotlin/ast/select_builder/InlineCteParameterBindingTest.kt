package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.manager.execute.query.BoundQuery
import gog.my_project.data_base.core.query.reader.BuiltQuery
import gog.my_project.data_base.query.api.interfaces.api.select_api.query_render_select.IQueryRenderSelectApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteAstComposer
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteSourceContext
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import gog.my_project.data_base.manager.execute.manager.QueryExecute
import java.lang.reflect.Proxy
import java.sql.PreparedStatement
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private data class ScopedValue(val value: Int)

private class ParameterizedInlineDeclaration(
    private val cteName: String,
    private val parameterName: String,
    private val nested: Cte? = null,
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
                    sideValueParam {
                        paramName(parameterName)
                        paramValue(params.value)
                    }
                }
            }
        }
    }
}

private data class JdbcBinding(val index: Int, val value: Any?, val sqlType: Int)

class InlineCteParameterBindingTest {
    @Test
    fun `inline nested relations bind by SQL order regardless of outer clause call order`() {
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
        val whereFirstResult = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(whereBeforeFrom.ast, dialect),
        )
        val fromFirstResult = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(fromBeforeWhere.ast, dialect),
        )
        val whereFirstTrackedSql = whereFirstResult.displaySql
        val whereFirstOccurrences = whereFirstResult.parameterOccurrences
        val fromFirstTrackedSql = fromFirstResult.displaySql
        val fromFirstOccurrences = fromFirstResult.parameterOccurrences
        val expectedPlaceholderOrder = listOf("inner_value", "parent_value", "outer_value")

        assertEquals(fromFirstSql, whereFirstSql)
        assertEquals(whereFirstSql, whereFirstTrackedSql)
        assertEquals(fromFirstSql, fromFirstTrackedSql)
        assertTrue(whereFirstSql.contains("As outer_source"), whereFirstSql)
        assertTrue(Regex("FROM\\s+\\(", RegexOption.IGNORE_CASE).containsMatchIn(whereFirstSql), whereFirstSql)
        assertEquals(expectedPlaceholderOrder, placeholderNames(whereFirstSql))

        val whereFirstParent = whereBeforeFrom.ast.table!!.derivedQuery!!
        val whereFirstNested = whereFirstParent.table!!.derivedQuery!!
        assertEquals("inner_query", whereFirstParent.table!!.metadata.cteRuntimeName)
        val expectedWhereFirstReferences = listOf(
            conditionParameter(whereFirstNested),
            conditionParameter(whereFirstParent),
            conditionParameter(whereBeforeFrom.ast),
        )
        assertEquals(listOf(11, 22, 33), whereFirstOccurrences.map { it.value })
        expectedWhereFirstReferences.indices.forEach { index ->
            val node = expectedWhereFirstReferences[index]
            assertEquals(node.metadata.name, whereFirstOccurrences[index].name)
            assertEquals(node.metadata.value, whereFirstOccurrences[index].value)
            assertEquals(node.metadata.sqlType, whereFirstOccurrences[index].sqlType)
        }

        val fromFirstParent = fromBeforeWhere.ast.table!!.derivedQuery!!
        val fromFirstNested = fromFirstParent.table!!.derivedQuery!!
        assertEquals("inner_query", fromFirstParent.table!!.metadata.cteRuntimeName)
        val expectedFromFirstReferences = listOf(
            conditionParameter(fromFirstNested),
            conditionParameter(fromFirstParent),
            conditionParameter(fromBeforeWhere.ast),
        )
        assertEquals(listOf(11, 22, 33), fromFirstOccurrences.map { it.value })
        expectedFromFirstReferences.indices.forEach { index ->
            val node = expectedFromFirstReferences[index]
            assertEquals(node.metadata.name, fromFirstOccurrences[index].name)
            assertEquals(node.metadata.value, fromFirstOccurrences[index].value)
            assertEquals(node.metadata.sqlType, fromFirstOccurrences[index].sqlType)
        }

        assertEquals(expectedPlaceholderOrder, whereFirstOccurrences.map { it.name })
        assertEquals(expectedPlaceholderOrder, fromFirstOccurrences.map { it.name })

        val expectedBindings = listOf(
            JdbcBinding(1, 11, Types.INTEGER),
            JdbcBinding(2, 22, Types.INTEGER),
            JdbcBinding(3, 33, Types.INTEGER),
        )
        assertEquals(expectedBindings, bindInSqlOrder(whereFirstResult))
        assertEquals(expectedBindings, bindInSqlOrder(fromFirstResult))
    }

    @Test
    fun `same parameter name with independent nested values is rejected instead of being resolved per scope`() {
        val query = buildThreeLevelQuery(
            whereBeforeFrom = true,
            innerParameterName = "shared_value",
            parentParameterName = "shared_value",
            outerParameterName = "shared_value",
        )
        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        val placeholders = placeholderNames(rendered.displaySql)

        assertEquals(listOf("shared_value", "shared_value", "shared_value"), placeholders)
        assertEquals(listOf(11, 22, 33), rendered.parameterOccurrences.map { it.value })

        val builtQuery = BuiltQuery(rendered.displaySql, rendered.parameterOccurrences.toMutableList())
        assertTrue(
            assertFailsWith<IllegalArgumentException> { builtQuery.getListParamNames() }
                .message.orEmpty().contains("Conflicting values supplied for parameter 'shared_value'"),
        )

        val boundCalls = mutableListOf<JdbcBinding>()
        val statement = recordingPreparedStatement(boundCalls)
        QueryExecute().readyParamsInQuery(
            statement,
            BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences),
        )
        assertEquals(
            listOf(
                JdbcBinding(1, 11, Types.INTEGER),
                JdbcBinding(2, 22, Types.INTEGER),
                JdbcBinding(3, 33, Types.INTEGER),
            ),
            boundCalls,
        )
    }

    private fun buildThreeLevelQuery(
        whereBeforeFrom: Boolean,
        innerParameterName: String,
        parentParameterName: String,
        outerParameterName: String,
    ): IQueryRenderSelectApi {
        val inner = ParameterizedInlineDeclaration(
            cteName = "inner_query",
            parameterName = innerParameterName,
        ).createCte(ScopedValue(11))
        val parent = ParameterizedInlineDeclaration(
            cteName = "parent_query",
            parameterName = parentParameterName,
            nested = inner,
        ).createCte(ScopedValue(22))

        val query = QueryRenderSelectBuilder()
        val whereBlock: IQueryRenderSelectApi.() -> Unit = {
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam {
                            paramName(outerParameterName)
                            paramValue(33)
                        }
                    }
                }
            }
        }
        val fromBlock: IQueryRenderSelectApi.() -> Unit = {
            CteAstComposer(ast, ModelTableAliasContext(), CteSourceContext())
                .addInlineCteSource(parent, "outer_source")
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

    private fun conditionParameter(ast: IQueryRenderSelectAst): IQueryParameterAst<*> =
        assertIs<IQueryParameterAst<*>>(
            (ast.where!!.condition!!.conditions.single() as IQueryConditionsAst).conditionSideRight,
        )

    private fun bindInSqlOrder(
        rendered: IdentityAwareQueryRenderResult,
    ): List<JdbcBinding> {
        val bindings = mutableListOf<JdbcBinding>()
        QueryExecute().readyParamsInQuery(
            recordingPreparedStatement(bindings),
            BoundQuery(rendered.jdbcSql, rendered.parameterOccurrences),
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
