package gog.my_project.data_base.query.renderer.parameters

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.core.query.reader.IQueryParameterBinding
import gog.my_project.data_base.core.query.reader.QueryParameterBinding
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_group.IQueryConditionsGroupsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item_collection.IQueryConditionsCollectionAst
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_item.QueryConditionsData
import gog.my_project.data_base.query.dialect.data_class.select_data.conditions_group.QueryConditionsGroupsData
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
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class InlineFilters(val value: Int)

private class InlineParameterDeclaration(
    private val cteName: String,
    private val parameterName: String,
    private val nested: Cte? = null,
) : ICteDeclaration<InlineFilters> {
    override fun createCte(params: InlineFilters): Cte = cte(cteName) {
        val sourceAlias = if (nested == null) "leaf_source" else "nested_source"
        if (nested == null) {
            from { table("leaf_table").alias(sourceAlias) }
        } else {
            withs { addWith { with(nested) } }
            from { cte(nested.name).alias(sourceAlias) }
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
                    sideValueParam {
                        paramName(parameterName)
                        paramValue(params.value)
                    }
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
        assertEquals(scalarResult.displaySql.replace(":minimum_age", "?"), scalarResult.jdbcSql)
        val scalarParameter = condition(scalar.ast).conditionSideRight as IQueryParameterAst<*>
        assertEquals(scalarParameter.metadata.name, scalarResult.parameterOccurrences.single().name)
        assertEquals(scalarParameter.metadata.value, scalarResult.parameterOccurrences.single().value)
        assertEquals(scalarParameter.metadata.sqlType, scalarResult.parameterOccurrences.single().sqlType)
        assertIs<QueryParameterBinding>(scalarResult.parameterOccurrences.single())

        val collection = QueryRenderSelectBuilder()
        collection.select { addColumn { column { tableAttribute("1") }; alias("value") } }
        collection.from { table("users").alias("u") }
        collection.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationIn()
                    sideValueCollection {
                        addParam {
                            paramName("user_id0")
                            paramValue(11)
                        }
                        addParam {
                            paramName("user_id1")
                            paramValue(22)
                        }
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
        assertEquals(
            collectionResult.displaySql.replace(":user_id0", "?").replace(":user_id1", "?"),
            collectionResult.jdbcSql,
        )
        assertEquals(listOf(11, 22), collectionResult.parameterOccurrences.map { it.value })
        val collectionParameters =
            (condition(collection.ast).conditionSideRight as IQueryConditionsCollectionAst)
                .paramsCollection.map { it as IQueryParameterAst<*> }
        assertEquals(listOf("user_id0", "user_id1"), collectionParameters.map { it.metadata.name })
        assertEquals(listOf(11, 22), collectionParameters.map { it.metadata.value })
    }

    @Test
    fun `missing AST parameter name or SQL type still fails metadata validation`() {
        val dialect = MySqlDialect()

        val missingNameQuery = simpleQuery("valid_name", 10)
        val missingNameParameter = condition(missingNameQuery.ast).conditionSideRight as IQueryParameterAst<*>
        missingNameParameter.metadata.name = null
        val nameFailure = assertFailsWith<IllegalArgumentException> {
            QueryParameterRendering.render(missingNameQuery.ast, dialect)
        }
        assertTrue(nameFailure.message.orEmpty().contains("Parameter AST metadata name is missing"))

        val missingSqlTypeQuery = simpleQuery("valid_type", 11)
        val missingSqlTypeParameter = condition(missingSqlTypeQuery.ast).conditionSideRight as IQueryParameterAst<*>
        missingSqlTypeParameter.metadata.sqlType = null
        val sqlTypeFailure = assertFailsWith<IllegalArgumentException> {
            QueryParameterRendering.render(missingSqlTypeQuery.ast, dialect)
        }
        assertTrue(sqlTypeFailure.message.orEmpty().contains("Parameter AST metadata SQL type is missing"))
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
                        sideValueParam {
                            paramName("shared")
                            paramValue(7)
                        }
                    }
                }
            }
        }

        val firstAstParameter = condition(query.ast, 0).conditionSideRight as IQueryParameterAst<*>
        condition(query.ast, 1).conditionSideRight = firstAstParameter
        val secondAstParameter = condition(query.ast, 1).conditionSideRight as IQueryParameterAst<*>
        assertSame(firstAstParameter, secondAstParameter)
        val result = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, MySqlDialect()),
        )
        assertEquals(2, result.parameterOccurrences.size)
        assertSame(result.parameterOccurrences[0], result.parameterOccurrences[1])
        assertEquals(firstAstParameter.metadata.name, result.parameterOccurrences[0].name)
        assertEquals(firstAstParameter.metadata.value, result.parameterOccurrences[0].value)
        assertEquals(firstAstParameter.metadata.sqlType, result.parameterOccurrences[0].sqlType)
        assertIs<QueryParameterBinding>(result.parameterOccurrences[0])
        assertEquals(listOf("shared", "shared"), result.parameterOccurrences.map { it.name })
        assertEquals(listOf(7, 7), result.parameterOccurrences.map { it.value })
        assertEquals(result.displaySql.replace(":shared", "?"), result.jdbcSql)
        assertTrue(result.jdbcSql.contains("?"), result.jdbcSql)
    }

    @Test
    fun `independent same-name bindings with different values and SQL types stay distinct`() {
        val query = twoConditionQuery()
        val integerParameter = SqlParameter("shared_value", 11, Types.INTEGER)
        val stringParameter = SqlParameter("shared_value", "22", Types.VARCHAR)
        condition(query.ast, 0).conditionSideRight = integerParameter
        condition(query.ast, 1).conditionSideRight = stringParameter

        val dialect = MySqlDialect()
        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, dialect),
        )
        val occurrences = rendered.parameterOccurrences

        assertEquals(listOf("shared_value", "shared_value"), occurrences.map { it.name })
        assertEquals(listOf(11, "22"), occurrences.map { it.value })
        assertEquals(listOf(Types.INTEGER, Types.VARCHAR), occurrences.map { it.sqlType })
        assertSame(integerParameter, occurrences[0])
        assertSame(stringParameter, occurrences[1])
    }

    @Test
    fun `nested parameterized CTE relations render in dependency-first SQL order for both clause call orders and dialects`() {
        val dialects: List<BaseSqlDialect> = listOf(MySqlDialect(), MariaDbDialect())
        for (dialect in dialects) {
            val whereFirst = threeLevelRelationQuery(whereBeforeFrom = true)
            val fromFirst = threeLevelRelationQuery(whereBeforeFrom = false)
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
            assertTrue(whereFirstResult.displaySql.lowercase().contains("with"), whereFirstResult.displaySql)
            val normalizedSql = whereFirstResult.displaySql.replace(Regex("\\s+"), " ").lowercase()
            assertTrue(normalizedSql.contains("from parent_cte"), whereFirstResult.displaySql)
            assertEquals(listOf(11, 22, 33), whereFirstResult.parameterOccurrences.map { it.value })
            assertEquals(listOf(11, 22, 33), fromFirstResult.parameterOccurrences.map { it.value })

            val whereFirstRelations = whereFirst.ast.withs!!.withs
            val whereFirstNested = whereFirstRelations.first().withBody!!
            val whereFirstParent = whereFirstRelations.last().withBody!!
            val expectedWhereFirst = listOf(
                condition(whereFirstNested).conditionSideRight,
                condition(whereFirstParent).conditionSideRight,
                condition(whereFirst.ast).conditionSideRight,
            )
            expectedWhereFirst.indices.forEach { index ->
                val expected = expectedWhereFirst[index] as IQueryParameterAst<*>
                val actual = whereFirstResult.parameterOccurrences[index]
                assertEquals(expected.metadata.name, actual.name)
                assertEquals(expected.metadata.value, actual.value)
                assertEquals(expected.metadata.sqlType, actual.sqlType)
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
            val normalizedSql = whereFirstResult.displaySql.replace(Regex("\\s+"), " ").lowercase()
            assertTrue(normalizedSql.contains("from ("), whereFirstResult.displaySql)
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
                        sideValueParam {
                            paramName("join_owner")
                            paramValue(11)
                        }
                    }
                }
            }
        }
        query.where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationGreaterThan()
                    sideValueParam {
                        paramName("minimum_id")
                        paramValue(22)
                    }
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
                    sideValueParam {
                        paramName("present")
                        paramValue(11)
                    }
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
        assertFalse(result.displaySql.contains(":param1"), result.displaySql)
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
    fun `emitted legacy binding mixed with AST binding is rejected at render time`() {
        val query = simpleQuery("defined_parameter", 10)
        val parameterNode = condition(query.ast).conditionSideRight as IQueryParameterAst<*>
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
                ): String {
                    val parameterContext = ctx as IParameterRenderContext
                    return " ${parameterContext.renderParameter(parameterNode)} " +
                        " ${parameterContext.renderParameter(injected)} "
                }
            },
        )

        val failure = assertFailsWith<UnsupportedOperationException> {
            QueryParameterRendering.render(query.ast, dialect)
        }
        assertTrue(failure.message.orEmpty().contains("cannot mix independent parameter AST nodes"))
    }

    @Test
    fun `collector mutation without parameter emission fails the occurrence count check`() {
        val query = simpleQuery("defined_parameter", 10)
        val definedNode = condition(query.ast).conditionSideRight as IQueryParameterAst<*>
        val definedParameter = QueryParameterBinding(
            name = requireNotNull(definedNode.metadata.name),
            value = definedNode.metadata.value,
            sqlType = requireNotNull(definedNode.metadata.sqlType),
        )
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
    fun `partial parameter occurrence is rolled back when a renderer returns null`() {
        val query = simpleQuery("rolled_back", 10)
        val parameter = condition(query.ast).conditionSideRight as IQueryParameterAst<*>
        val dialect = MySqlDialect()
        var occurrences: MutableList<IQueryParameterBinding>? = null
        dialect._registry.register(
            IQueryConditionsAst::class,
            object : IAstRenderer<IQueryConditionsAst, QueryConditionsData?>,
                IParameterOccurrenceAwareRenderer {
                override fun render(
                    ast: IQueryConditionsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsData?,
                ): String? {
                    occurrences = ctx.parameterOccurrences
                    (ctx as IParameterRenderContext).renderParameter(parameter)
                    return null
                }
            },
        )

        val rendered = assertIs<LegacyQueryRenderResult>(
            QueryParameterRendering.render(query.ast, dialect),
        )

        assertTrue(occurrences!!.isEmpty())
        assertFalse(requireNotNull(rendered.sql).contains(":rolled_back"), rendered.sql)
    }

    @Test
    fun `nested null rollback preserves occurrences emitted before its checkpoint`() {
        val query = twoConditionQuery()
        val conditions = query.ast.where!!.condition!!.conditions
        val first = (conditions[0] as IQueryConditionsAst).conditionSideRight as IQueryParameterAst<*>
        val secondCondition = conditions[1] as IQueryConditionsAst
        val second = secondCondition.conditionSideRight as IQueryParameterAst<*>
        val dialect = MySqlDialect()
        dialect._registry.register(
            IQueryConditionsAst::class,
            object : IAstRenderer<IQueryConditionsAst, QueryConditionsData?>,
                IParameterOccurrenceAwareRenderer {
                override fun render(
                    ast: IQueryConditionsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsData?,
                ): String? {
                    (ctx as IParameterRenderContext).renderParameter(second)
                    return null
                }
            },
        )
        dialect._registry.register(
            IQueryConditionsGroupsAst::class,
            object : IAstRenderer<IQueryConditionsGroupsAst, QueryConditionsGroupsData?>,
                IParameterOccurrenceAwareRenderer {
                override fun render(
                    ast: IQueryConditionsGroupsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsGroupsData?,
                ): String {
                    val firstPlaceholder = (ctx as IParameterRenderContext).renderParameter(first)
                    assertEquals(null, ctx.registry.render(secondCondition, ctx.dialect, QueryConditionsData()))
                    assertEquals(first.metadata.name, ctx.parameterOccurrences?.singleOrNull()?.name)
                    return " ( $firstPlaceholder ) "
                }
            },
        )

        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.render(query.ast, dialect),
        )

        assertEquals(listOf(first.metadata.name), rendered.parameterOccurrences.map { it.name })
        assertEquals(listOf(first.metadata.value), rendered.parameterOccurrences.map { it.value })
    }

    @Test
    fun `renderer exception rolls back partial occurrences and rethrows the same failure`() {
        val query = simpleQuery("exception_value", 10)
        val parameter = condition(query.ast).conditionSideRight as IQueryParameterAst<*>
        val dialect = MySqlDialect()
        val expected = IllegalStateException("renderer failed after emitting a parameter")
        var occurrences: MutableList<IQueryParameterBinding>? = null
        dialect._registry.register(
            IQueryConditionsAst::class,
            object : IAstRenderer<IQueryConditionsAst, QueryConditionsData?>,
                IParameterOccurrenceAwareRenderer {
                override fun render(
                    ast: IQueryConditionsAst,
                    ctx: IRenderContext,
                    dataClass: QueryConditionsData?,
                ): String? {
                    occurrences = ctx.parameterOccurrences
                    (ctx as IParameterRenderContext).renderParameter(parameter)
                    throw expected
                }
            },
        )

        val actual = assertFailsWith<IllegalStateException> {
            QueryParameterRendering.render(query.ast, dialect)
        }

        assertSame(expected, actual)
        assertTrue(occurrences!!.isEmpty())
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
        val secondParameter = condition(second.ast).conditionSideRight as IQueryParameterAst<*>
        assertEquals(secondParameter.metadata.name, secondResult.parameterOccurrences.single().name)
        assertEquals(secondParameter.metadata.value, secondResult.parameterOccurrences.single().value)
        assertIs<QueryParameterBinding>(secondResult.parameterOccurrences.single())
        assertNotSame(firstResult.parameterOccurrences.single(), secondResult.parameterOccurrences.single())
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
                        sideValueParam {
                            paramName(name)
                            paramValue(value)
                        }
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
                        sideValueParam {
                            paramName("initial_$index")
                            paramValue(index)
                        }
                    }
                }
            }
        }
    }

    private fun threeLevelRelationQuery(whereBeforeFrom: Boolean): QueryRenderSelectBuilder {
        val leaf = InlineParameterDeclaration("leaf_cte", "shared_value")
            .createCte(InlineFilters(11))
        val parent = InlineParameterDeclaration(
            "parent_cte",
            "shared_value",
            nested = leaf,
        ).createCte(InlineFilters(22))
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
            withs { addWith { with(parent) } }
            from { cte(parent.name).alias("outer_source") }
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
                                sideValueParam {
                                    paramName("cte_value")
                                    paramValue(11)
                                }
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
                        sideValueParam {
                            paramName("outer_value")
                            paramValue(33)
                        }
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
                                sideValueParam {
                                    paramName("derived_value")
                                    paramValue(22)
                                }
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

    @Test
    fun `concat renders its column parts with column renderer data`() {
        val query = QueryRenderSelectBuilder()
        query.from { table("user_users").alias("uu") }
        query.select {
            addColumn {
                methodConcat {
                    column {
                        tableSelector("uu")
                        columnSelector("name")
                    }
                    string("-")
                    column {
                        tableSelector("uu")
                        columnSelector("family")
                    }
                }
                alias("user_full_name")
            }
        }

        val sql = requireNotNull(MySqlDialect().render(query.ast))

        assertTrue(sql.contains("concat( uu.name , '-',  uu.family )"), sql)
        assertTrue(sql.contains("As user_full_name"), sql)
    }

    private fun condition(ast: IQueryRenderSelectAst, index: Int = 0): IQueryConditionsAst =
        ast.where!!.condition!!.conditions[index] as IQueryConditionsAst
}
