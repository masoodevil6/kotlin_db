package gog.my_project.data_base.query.renderer.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item.QueryConditionsData
import gog.my_project.data_base.query.dialect.manager.BaseSqlDialect
import gog.my_project.data_base.query.renderer.dialects.MariaDbDialect
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.interfaces.IAstRenderer
import gog.my_project.data_base.query.renderer.interfaces.IParameterOccurrenceAwareRenderer
import gog.my_project.data_base.query.renderer.interfaces.IParameterRenderContext
import gog.my_project.data_base.query.renderer.interfaces.IRenderContext
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class InlineFilters(val value: Int)

private class InlineParameterDeclaration(
    override val params: InlineFilters,
    private val cteName: String,
    private val parameterName: String,
    private val nested: InlineParameterDeclaration? = null,
) : ICteDeclaration<InlineFilters> {
    override fun createCte(params: InlineFilters): Cte = cte(cteName) {
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
                    sideSelector { tableAttribute("1") }
                    operationEqual()
                    sideValue(parameterName, params.value)
                }
            }
        }
    }
}

class QueryParameterRenderingTest {

    @Test
    fun `scalar and collection emit positional SQL and occurrence references in order`() {
        val scalar = simpleQuery("minimum_age", 18)
        val dialect = MySqlDialect()
        val scalarResult = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(scalar.ast, dialect),
        )

        assertEquals(dialect.render(scalar.ast), scalarResult.displaySql)
        assertTrue(scalarResult.displaySql.contains(":minimum_age"), scalarResult.displaySql)
        assertTrue(scalarResult.jdbcSql.contains("?"), scalarResult.jdbcSql)
        assertSame(scalar.params.single(), scalarResult.parameterOccurrences.single())

        val collection = QueryRenderSelectBuilder()
        collection.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        collection.from { table("users").alias("u") }
        collection.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationIn()
                    sideValueCollection("user_id") {
                        addParam(11)
                        addParam(22)
                    }
                }
            }
        }

        val collectionResult = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(collection.ast, dialect),
        )
        assertEquals(dialect.render(collection.ast), collectionResult.displaySql)
        assertTrue(collectionResult.displaySql.contains(":user_id0"), collectionResult.displaySql)
        assertTrue(collectionResult.displaySql.contains(":user_id1"), collectionResult.displaySql)
        assertTrue(collectionResult.jdbcSql.lowercase().contains("in"), collectionResult.jdbcSql)
        assertEquals(listOf(11, 22), collectionResult.parameterOccurrences.map { it.value })
        collection.params.indices.forEach { assertSame(collection.params[it], collectionResult.parameterOccurrences[it]) }
    }

    @Test
    fun `repeated rendering of the same binding creates one occurrence per SQL emission`() {
        val query = QueryRenderSelectBuilder()
        query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                repeat(2) {
                    addCondition {
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValue("shared", 7)
                    }
                }
            }
        }

        val firstAstParameter = condition(query.ast, 0).conditionSideRight as SqlParameter<*>
        val secondAstParameter = condition(query.ast, 1).conditionSideRight as SqlParameter<*>
        assertSame(firstAstParameter, secondAstParameter)

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertEquals(2, result.parameterOccurrences.size)
        assertSame(firstAstParameter, result.parameterOccurrences[0])
        assertSame(firstAstParameter, result.parameterOccurrences[1])
        assertTrue(result.jdbcSql.contains("?"), result.jdbcSql)
    }

    @Test
    fun `independent same-name bindings with different values and SQL types stay distinct`() {
        val query = twoConditionQuery()
        val integerParameter = SqlParameter("shared_value", 11, Types.INTEGER)
        val stringParameter = SqlParameter("shared_value", "22", Types.VARCHAR)
        condition(query.ast, 0).conditionSideRight = integerParameter
        condition(query.ast, 1).conditionSideRight = stringParameter

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )

        assertEquals(listOf("shared_value", "shared_value"), result.parameterOccurrences.map { it.name })
        assertEquals(listOf(11, "22"), result.parameterOccurrences.map { it.value })
        assertEquals(listOf(Types.INTEGER, Types.VARCHAR), result.parameterOccurrences.map { it.sqlType })
        assertSame(integerParameter, result.parameterOccurrences[0])
        assertSame(stringParameter, result.parameterOccurrences[1])
    }

    @Test
    fun `nested CTE declarations render inline in SQL order for both clause call orders and dialects`() {
        val dialects: List<BaseSqlDialect> = listOf(MySqlDialect(), MariaDbDialect())
        for (dialect in dialects) {
            val whereFirst = threeLevelInlineQuery(whereBeforeFrom = true)
            val fromFirst = threeLevelInlineQuery(whereBeforeFrom = false)
            val whereFirstResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.render(whereFirst.ast, dialect),
            )
            val fromFirstResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.render(fromFirst.ast, dialect),
            )

            assertEquals(dialect.render(whereFirst.ast), whereFirstResult.displaySql)
            assertEquals(dialect.render(fromFirst.ast), fromFirstResult.displaySql)
            assertEquals(whereFirstResult.displaySql, fromFirstResult.displaySql)
            assertEquals(whereFirstResult.jdbcSql, fromFirstResult.jdbcSql)
            assertTrue(whereFirstResult.displaySql.lowercase().contains("from  ("), whereFirstResult.displaySql)
            assertTrue(whereFirstResult.jdbcSql.lowercase().contains("from  ("), whereFirstResult.jdbcSql)
            assertFalse(whereFirstResult.displaySql.lowercase().contains("with"), whereFirstResult.displaySql)
            assertEquals(listOf(11, 22, 33), whereFirstResult.parameterOccurrences.map { it.value })
            assertEquals(listOf(11, 22, 33), fromFirstResult.parameterOccurrences.map { it.value })

            val whereFirstParent = whereFirst.ast.table!!.derivedQuery!!
            val whereFirstNested = whereFirstParent.table!!.derivedQuery!!
            val expectedWhereFirst = listOf(
                condition(whereFirstNested).conditionSideRight,
                condition(whereFirstParent).conditionSideRight,
                condition(whereFirst.ast).conditionSideRight,
            )
            expectedWhereFirst.indices.forEach { index ->
                assertSame(expectedWhereFirst[index], whereFirstResult.parameterOccurrences[index])
            }
        }
    }

    @Test
    fun `explicit WITH derived SELECT and outer WHERE share one emission-ordered result`() {
        val dialects: List<BaseSqlDialect> = listOf(MySqlDialect(), MariaDbDialect())
        for (dialect in dialects) {
            val whereFirst = withDerivedQuery(whereBeforeFrom = true)
            val fromFirst = withDerivedQuery(whereBeforeFrom = false)
            val whereFirstResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.render(whereFirst.ast, dialect),
            )
            val fromFirstResult = assertIs<IdentityAwareQueryRenderResult>(
                QueryParameterRendering.render(fromFirst.ast, dialect),
            )

            assertEquals(dialect.render(whereFirst.ast), whereFirstResult.displaySql)
            assertEquals(whereFirstResult.displaySql, fromFirstResult.displaySql)
            assertEquals(whereFirstResult.jdbcSql, fromFirstResult.jdbcSql)
            assertTrue(whereFirstResult.displaySql.contains("with"), whereFirstResult.displaySql)
            assertTrue(whereFirstResult.displaySql.lowercase().contains("from  ("), whereFirstResult.displaySql)
            assertEquals(listOf("cte_value", "derived_value", "outer_value"),
                whereFirstResult.parameterOccurrences.map { it.name })
            assertEquals(listOf(11, 22, 33), whereFirstResult.parameterOccurrences.map { it.value })
            assertEquals(listOf(11, 22, 33), fromFirstResult.parameterOccurrences.map { it.value })
        }
    }

    @Test
    fun `JOIN parameters are recorded before outer WHERE parameters`() {
        val query = QueryRenderSelectBuilder()
        query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        query.from { table("users").alias("u") }
        query.joins {
            addJoin {
                innerJoin()
                table { table("details").alias("d") }
                condition {
                    addCondition {
                        sideSelector { tableColumn("d", "owner_id") }
                        operationEqual()
                        sideValue("join_owner", 11)
                    }
                }
            }
        }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationGreaterThan()
                    sideValue("minimum_id", 22)
                }
            }
        }

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertEquals(listOf("join_owner", "minimum_id"), result.parameterOccurrences.map { it.name })
        assertEquals(listOf(11, 22), result.parameterOccurrences.map { it.value })
        assertTrue(result.displaySql.indexOf(":join_owner") < result.displaySql.indexOf(":minimum_id"))
        val jdbcSqlLowercase = result.jdbcSql.lowercase()
        assertTrue(jdbcSqlLowercase.indexOf("join") < jdbcSqlLowercase.indexOf("where"))
    }

    @Test
    fun `omitted condition does not leave an occurrence in identity rendering`() {
        val query = QueryRenderSelectBuilder()
        query.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        query.from { table("users").alias("u") }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    sideValue("present", 11)
                }
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    // No right operand means this condition is omitted by the built-in renderer.
                }
            }
        }

        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertEquals(listOf("present"), result.parameterOccurrences.map { it.name })
        assertEquals(listOf(11), result.parameterOccurrences.map { it.value })
        assertTrue(result.displaySql.contains(":present"), result.displaySql)
    }

    @Test
    fun `legacy-only AST keeps named rendering and mixed AST fails without fallback`() {
        val legacy = simpleQuery("legacy_placeholder", 1)
        condition(legacy.ast).conditionSideRight = "legacy_sql_expression"
        val dialect = MySqlDialect()
        val legacyResult = assertIs<LegacyQueryRenderResult>(QueryParameterRendering.render(legacy.ast, dialect))
        assertEquals(dialect.render(legacy.ast), legacyResult.sql)

        val mixed = twoConditionQuery()
        condition(mixed.ast, 1).conditionSideRight = "legacy_sql_expression"
        assertFailsWith<UnsupportedOperationException> {
            QueryParameterRendering.render(mixed.ast, dialect)
        }
    }

    @Test
    fun `unverified custom renderer is rejected by identity-aware rendering`() {
        val query = simpleQuery("verified_parameter", 10)
        val dialect = MySqlDialect()
        dialect._registry.register(
            IQueryConditionsAst::class,
            object : IAstRenderer<IQueryConditionsAst, QueryConditionsData?> {
                override fun render(
                    ast: IQueryConditionsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsData?,
                ): String = ":untracked_custom_placeholder"
            },
        )

        val failure = assertFailsWith<UnsupportedOperationException> {
            QueryParameterRendering.render(query.ast, dialect)
        }
        assertTrue(failure.message.orEmpty().contains("unverified renderer"))
    }

    @Test
    fun `occurrence outside inspected AST is rejected`() {
        val query = simpleQuery("defined_parameter", 10)
        val injected = SqlParameter("unexpected_parameter", 99, Types.INTEGER)
        val dialect = MySqlDialect()
        dialect._registry.register(
            IQueryConditionsAst::class,
            object : IAstRenderer<IQueryConditionsAst, QueryConditionsData?>,
                IParameterOccurrenceAwareRenderer {
                override fun render(
                    ast: IQueryConditionsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsData?,
                ): String = " ${(ctx as IParameterRenderContext).renderParameter(injected)} "
            },
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            QueryParameterRendering.render(query.ast, dialect)
        }
        assertTrue(failure.message.orEmpty().contains("not defined in the inspected query AST"))
    }

    @Test
    fun `collector mutation without parameter emission fails the occurrence count check`() {
        val query = simpleQuery("defined_parameter", 10)
        val definedParameter = condition(query.ast).conditionSideRight as SqlParameter<*>
        val dialect = MySqlDialect()
        dialect._registry.register(
            IQueryConditionsAst::class,
            object : IAstRenderer<IQueryConditionsAst, QueryConditionsData?>,
                IParameterOccurrenceAwareRenderer {
                override fun render(
                    ast: IQueryConditionsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsData?,
                ): String {
                    requireNotNull(ctx.parameterOccurrences).add(definedParameter)
                    return ":${definedParameter.name}"
                }
            },
        )

        val failure = assertFailsWith<IllegalStateException> {
            QueryParameterRendering.render(query.ast, dialect)
        }
        assertTrue(failure.message.orEmpty().contains("emission count 0"))
        assertTrue(failure.message.orEmpty().contains("occurrence count 1"))
    }

    @Test
    fun `render sessions do not leak occurrences between queries`() {
        val dialect = MySqlDialect()
        val first = simpleQuery("first_value", 11)
        val second = simpleQuery("second_value", 22)

        val firstResult = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(first.ast, dialect),
        )
        val secondResult = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(second.ast, dialect),
        )

        assertEquals(listOf(11), firstResult.parameterOccurrences.map { it.value })
        assertEquals(listOf(22), secondResult.parameterOccurrences.map { it.value })
        assertSame(second.params.single(), secondResult.parameterOccurrences.single())
        assertFalse(secondResult.displaySql.contains(":first_value"), secondResult.displaySql)
    }

    private fun simpleQuery(name: String, value: Any): QueryRenderSelectBuilder =
        QueryRenderSelectBuilder().apply {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
            from { table("users").alias("u") }
            where {
                conditions {
                    addCondition {
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValue(name, value)
                    }
                }
            }
        }

    private fun twoConditionQuery(): QueryRenderSelectBuilder = QueryRenderSelectBuilder().apply {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
        from { table("users").alias("u") }
        where {
            conditions {
                repeat(2) { index ->
                    addCondition {
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValue("initial_$index", index)
                    }
                }
            }
        }
    }

    private fun threeLevelInlineQuery(whereBeforeFrom: Boolean): QueryRenderSelectBuilder {
        val leaf = InlineParameterDeclaration(InlineFilters(11), "leaf_cte", "shared_value")
        val parent = InlineParameterDeclaration(
            InlineFilters(22),
            "parent_cte",
            "shared_value",
            nested = leaf,
        )
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
            addColumn { column { tableColumn("outer_source", "value") }; alias("result") }
        }
        return query
    }

    private fun withDerivedQuery(whereBeforeFrom: Boolean): QueryRenderSelectBuilder {
        val query = QueryRenderSelectBuilder()
        query.withs {
            addWith {
                with("seed_cte") {
                    from { table("seed_rows").alias("seed_row") }
                    select {
                        addColumn { column { tableAttribute("1") }; alias("value") }
                    }
                    where {
                        conditions {
                            addCondition {
                                sideSelector { tableAttribute("1") }
                                operationEqual()
                                sideValue("cte_value", 11)
                            }
                        }
                    }
                }
            }
        }

        val whereBlock: QueryRenderSelectBuilder.() -> Unit = {
            where {
                conditions {
                    addCondition {
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValue("outer_value", 33)
                    }
                }
            }
        }
        val fromBlock: QueryRenderSelectBuilder.() -> Unit = {
            from {
                table {
                    from { cte("seed_cte").alias("seed_source") }
                    select {
                        addColumn {
                            column { tableColumn("seed_source", "value") }
                            alias("value")
                        }
                    }
                    where {
                        conditions {
                            addCondition {
                                sideSelector { tableAttribute("1") }
                                operationEqual()
                                sideValue("derived_value", 22)
                            }
                        }
                    }
                }.alias("derived_source")
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
                column { tableColumn("derived_source", "value") }
                alias("result")
            }
        }
        return query
    }

    private fun condition(ast: IQueryRenderSelectAst, index: Int = 0): IQueryConditionsAst =
        ast.where!!.condition!!.conditions[index] as IQueryConditionsAst
}
