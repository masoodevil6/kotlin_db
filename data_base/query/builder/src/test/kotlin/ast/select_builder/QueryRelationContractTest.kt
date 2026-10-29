package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.parameter.IQueryParameterAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.condition_item.IQueryConditionsAst
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.insert_builder.query_render_insert.QueryRenderInsertBuilder
import gog.my_project.data_base.query.builder.ast.update_builder.query_render_update.QueryRenderUpdateBuilder
import gog.my_project.data_base.query.builder.ast.delete_builder.query_render_delete.QueryRenderDeleteBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.joins_item.QueryJoinsItemBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.assertIs

private data class BoundValue(val value: Int)
private class InvocationCount(var value: Int = 0)

private class BoundParamQuery(
    private val queryName: String,
    private val parameterName: String,
    private val invocations: InvocationCount,
) : ICteDeclaration<BoundValue> {
    override fun createCte(params: BoundValue): Cte {
        invocations.value++
        return cte(queryName) {
            from { table("${queryName}_source").alias("s") }
            select {
                addColumn {
                    column { tableColumn("s", "id") }
                    alias("id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("s", "id") }
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
}

private class DependentBoundParamQuery(
    private val dependency: Cte,
    private val invocations: InvocationCount,
) : ICteDeclaration<BoundValue> {
    override fun createCte(params: BoundValue): Cte {
        invocations.value++
        return cte("parent_query") {
            withs { addWith { with(dependency) } }
            from {
                cte(dependency.name)
                alias("child_source")
            }
            select {
                addColumn {
                    column { tableAttribute("1") }
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
                            paramName("parent_value")
                            paramValue(params.value)
                        }
                    }
                }
            }
        }
    }
}

@QBTable(name = "relation_users", alias = "ru")
private class RelationUserModel : IModelBase {
    @QBColumn(name = "user_id", alias = "physical_user_id")
    val id: Long = 0
}

class QueryRelationContractTest {

    @Test
    fun `cte exposes the mutable query api directly as its definition`() {
        val cte = cte("direct_definition") {
            from { table("source") }
            select {
                addColumn {
                    column { tableAttribute("1") }
                    alias("value")
                }
            }
        }

        assertTrue(cte.definition is QueryRenderSelectBuilder)
        assertIs<IQueryRenderSelectAst>(cte.definition.ast)
    }

    @Test
    fun `parameter changes before composition are read in dependency-first bind order`() {
        val cte = cte("mutable_params_before") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("cte_value")
                            paramValue(1)
                        }
                    }
                }
            }
        }
        val cteAst = assertIs<IQueryRenderSelectAst>(cte.definition.ast)
        parameterIn(cteAst).metadata.value = 23

        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .from { cte(cte.name) }
            .select { addColumn { column { cteColumn(cte, "id") } } }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam {
                            paramName("outer_value")
                            paramValue(31)
                        }
                    }
                }
            }

        val sql = MySqlDialect().render(consumer.ast)!!
        assertTrue(sql.indexOf(":cte_value") < sql.indexOf(":outer_value"), sql)
        val occurrences = renderedParameterResult(consumer.ast).parameterOccurrences
        assertEquals(listOf("cte_value", "outer_value"), occurrences.map { it.name })
        assertEquals(listOf(23, 31), occurrences.map { it.value })
    }

    @Test
    fun `replacement AST root before composition supplies the composed CTE body`() {
        val cte = cte("replacement_before") {
            from { table("old_source").alias("o") }
            select {
                addColumn {
                    column { tableColumn("o", "id") }
                    alias("old_id")
                }
            }
        }
        val replacement = cte("replacement_template") {
            from { table("new_source").alias("n") }
            select {
                addColumn {
                    column { tableColumn("n", "id") }
                    alias("new_id")
                }
            }
        }
        cte.definition.ast = replacement.definition.ast

        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .from { cte(cte.name) }
            .select { addColumn { column { cteColumn(cte, "new_id") } } }

        val body = consumer.ast.withs!!.withs.single().withBody!!
        val sql = MySqlDialect().render(consumer.ast)!!
        assertEquals("new_id", body.select!!.columns.single().metadata.ColumnAlias)
        assertTrue(sql.contains("new_source"), sql)
        assertTrue(sql.contains("As new_id"), sql)
    }

    @Test
    fun `CTE composition retains the AST parameter definition through rendering`() {
        val cte = cte("mutable_params_after") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("cte_value")
                            paramValue(5)
                        }
                    }
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .from { cte(cte.name) }
            .select { addColumn { column { cteColumn(cte, "id") } } }

        val definitionAst = assertIs<IQueryRenderSelectAst>(cte.definition.ast)
        val definitionParameter = parameterIn(definitionAst)
        val composedBody = consumer.ast.withs!!.withs.single().withBody!!
        assertSame(definitionParameter, parameterIn(composedBody))

        definitionParameter.metadata.value = 9
        val occurrences = renderedParameterResult(consumer.ast).parameterOccurrences
        assertEquals(listOf("cte_value"), occurrences.map { it.name })
        assertEquals(listOf(9), occurrences.map { it.value })
    }

    @Test
    fun `replacement AST root after composition does not replace the existing CTE body`() {
        val cte = cte("replacement_after") {
            from { table("old_source").alias("o") }
            select {
                addColumn {
                    column { tableColumn("o", "id") }
                    alias("old_id")
                }
            }
        }
        val replacement = cte("replacement_template_after") {
            from { table("new_source").alias("n") }
            select {
                addColumn {
                    column { tableColumn("n", "id") }
                    alias("new_id")
                }
            }
        }
        val originalDefinitionAst = cte.definition.ast
        val originalSelect = originalDefinitionAst.select
        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .from { cte(cte.name) }
            .select { addColumn { column { cteColumn(cte, "old_id") } } }
        val composedBody = consumer.ast.withs!!.withs.single().withBody!!

        cte.definition.ast = replacement.definition.ast

        assertSame(composedBody, consumer.ast.withs!!.withs.single().withBody)
        assertSame(originalSelect, composedBody.select)
        assertNotSame(replacement.definition.ast, composedBody)
        val sql = MySqlDialect().render(consumer.ast)!!
        assertTrue(sql.contains("old_source"), sql)
        assertTrue(sql.contains("As old_id"), sql)
        assertFalse(sql.contains("new_source"), sql)
    }

    @Test
    fun `in-place mutation of a shared SELECT child is visible after composition`() {
        val cte = cte("shared_select_child") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("original_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .from { cte(cte.name) }
            .select {
                addColumn {
                    column { tableAttribute("1") }
                    alias("constant")
                }
            }
        val composedBody = consumer.ast.withs!!.withs.single().withBody!!
        val definitionSelect = cte.definition.ast.select!!

        cte.definition.ast.select!!.columns.single().metadata.ColumnAlias = "updated_id"

        assertSame(definitionSelect, composedBody.select)
        assertEquals("updated_id", composedBody.select!!.columns.single().metadata.ColumnAlias)
        assertTrue(MySqlDialect().render(consumer.ast)!!.contains("As updated_id"))
    }

    @Test
    fun `model source uses the new model entry while preserving resolved table metadata`() {
        val query = QueryRenderSelectBuilder().from { model(RelationUserModel::class) }

        assertEquals("relation_users", query.ast.table!!.metadata.nameFromAnnotation)
        assertEquals("ru", query.ast.table!!.metadata.aliasFromAnnotation)
    }

    @Test
    fun `inline SELECT source renders as a derived table and preserves parameter order`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableColumn("q", "id") }
                    alias("id")
                }
            }
            .from {
                table {
                    select {
                        addColumn {
                            column { tableAttribute("1") }
                            alias("id")
                        }
                    }
                    from { table("inner_users").alias("iu") }
                    where {
                        conditions {
                            addCondition {
                                logicalAnd()
                                sideSelector { tableColumn("iu", "id") }
                                operationEqual()
                                sideValueParam {
                                    paramName("nested_id")
                                    paramValue(7)
                                }
                            }
                        }
                    }
                }
                alias("q")
            }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("q", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("outer_id")
                            paramValue(9)
                        }
                    }
                }
            }

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(Regex("FROM\\s+\\(\\s*SELECT", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertTrue(sql.contains("inner_users"), sql)
        assertTrue(sql.contains("As q"), sql)
        assertTrue(sql.indexOf(":nested_id") < sql.indexOf(":outer_id"), sql)
        val occurrences = renderedParameterResult(query.ast).parameterOccurrences
        assertEquals(listOf("nested_id", "outer_id"), occurrences.map { it.name })
        assertEquals(listOf(7, 9), occurrences.map { it.value })
    }

    @Test
    fun `composed declaration relations read explicit parameters once and preserve dependency parameter order`() {
        val childCalls = InvocationCount()
        val parentCalls = InvocationCount()
        val childDeclaration = BoundParamQuery("child_query", "child_value", childCalls)
        val child = childDeclaration.createCte(BoundValue(17))
        val parentDeclaration = DependentBoundParamQuery(child, parentCalls)
        val parent = parentDeclaration.createCte(BoundValue(29))

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(parent) } }
            .select { addColumn { column { tableAttribute("1") }; alias("result") } }
            .from {
                cte(parent.name).alias("parent_source")
            }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValueParam {
                            paramName("outer_value")
                            paramValue(41)
                        }
                    }
                }
            }

        val sql = MySqlDialect().render(query.ast)!!
        val normalizedSql = sql.replace(Regex("\\s+"), " ").lowercase()
        assertEquals(1, parentCalls.value)
        assertEquals(1, childCalls.value)
        assertEquals(listOf("child_query", "parent_query"), query.ast.withs!!.withs.map { it.metadata.withName })
        assertEquals("parent_query", query.ast.table!!.metadata.resolveName())
        assertEquals("parent_source", query.ast.table!!.metadata.aliasFromAst)
        val parentDefinition = query.ast.withs!!.withs.last().withBody!!
        assertEquals("child_query", parentDefinition.table!!.metadata.resolveName())
        assertEquals("child_source", parentDefinition.table!!.metadata.aliasFromAst)
        val occurrences = renderedParameterResult(query.ast).parameterOccurrences
        assertEquals(listOf("child_value", "parent_value", "outer_value"), occurrences.map { it.name })
        assertEquals(listOf(17, 29, 41), occurrences.map { it.value })
        assertTrue(normalizedSql.contains("child_query as"), sql)
        assertTrue(normalizedSql.contains("parent_query as"), sql)
        assertTrue(normalizedSql.contains("parent_query as parent_source"), sql)
        assertTrue(normalizedSql.contains("as child_source"), sql)
        assertTrue(normalizedSql.contains("as parent_source"), sql)
        assertEquals(1, Regex(Regex.escape(":child_value")).findAll(sql).count(), sql)
        assertEquals(1, Regex(Regex.escape(":parent_value")).findAll(sql).count(), sql)
        assertEquals(1, Regex(Regex.escape(":outer_value")).findAll(sql).count(), sql)
        assertTrue(sql.indexOf(":child_value") < sql.indexOf(":parent_value"), sql)
        assertTrue(sql.indexOf(":parent_value") < sql.indexOf(":outer_value"), sql)
    }

    @Test
    fun `query-only table sources are rejected in CRUD and JOIN table contexts`() {
        val invocations = InvocationCount()
        val declaration = BoundParamQuery("not_used", "not_used", invocations)
        val relation = declaration.createCte(BoundValue(3))

        assertFailsWith<IllegalStateException> {
            QueryRenderInsertBuilder().table { cte(relation) }
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderUpdateBuilder().table { table { select { } } }
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderDeleteBuilder().table { cte(relation) }
        }
        assertFailsWith<IllegalStateException> {
            QueryJoinsItemBuilder().table { table { select { } } }
        }
        assertEquals(1, invocations.value)
    }

    @Test
    fun `from table block preserves string source alias and query parameters`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("user_id")
                }
            }
            .from { table("users").alias("u") }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("id")
                            paramValue(7)
                        }
                    }
                }
            }

        assertEquals("users", query.ast.table!!.metadata.nameFromAst)
        assertEquals("u", query.ast.table!!.metadata.aliasFromAst)
        val occurrences = renderedParameterResult(query.ast).parameterOccurrences
        assertEquals(listOf("id"), occurrences.map { it.name })
        assertEquals(listOf(7), occurrences.map { it.value })

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("users"))
        assertTrue(sql.contains("As u"))
        assertTrue(sql.contains("u.id"))
    }

    @Test
    fun `SQL aliases lower to existing CTE and CTE column nodes`() {
        val relation = cte("user_stats") {
            from { model(RelationUserModel::class) }
            select {
                addColumn {
                    column { tableSelector(RelationUserModel::class); columnSelector(RelationUserModel::class, RelationUserModel::id) }
                    alias("user_id")
                }
                addColumn {
                    methodCount {
                        addColumn {
                            tableSelector(RelationUserModel::class)
                            columnSelector(RelationUserModel::class, RelationUserModel::id)
                        }
                    }
                    alias("total")
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(relation) } }
            .from { cte(relation.name).alias("stats") }
            .select {
                addColumn { column { cteColumn(relation, "user_id") } }
                addColumn { column { cteColumn(relation, "total") } }
            }

        assertEquals("user_stats", query.ast.withs!!.withs.single().metadata.withName)
        assertEquals("user_stats", query.ast.table!!.metadata.nameFromAst)
        assertEquals("stats", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("stats", query.ast.select!!.columns.first().Column!!.metadata.resolveTableAlias())
        assertEquals("user_id", query.ast.select!!.columns.first().Column!!.metadata.resolveColumnName())
        assertEquals("total", query.ast.select!!.columns[1].Column!!.metadata.resolveColumnName())

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("user_stats AS"))
        assertTrue(sql.contains("stats.user_id"))
        assertTrue(sql.contains("stats.total"))
        assertTrue(sql.contains("As user_id"))
        assertTrue(sql.contains("count("))
        assertTrue(sql.contains("As total"))
    }

    @Test
    fun `the SELECT alias is the exact name consumed by a relation`() {
        val relation = cte("aliased_stats") {
            from { model(RelationUserModel::class) }
            select {
                addColumn {
                    column { tableSelector(RelationUserModel::class); columnSelector(RelationUserModel::class, RelationUserModel::id) }
                    alias("published_user_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .from { cte(relation).alias("published") }
            .select { addColumn { column { cteColumn(relation, "published_user_id") } } }

        assertTrue(MySqlDialect().render(consumer.ast)!!.contains("published.published_user_id"))
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { cte(relation) }.select {
                addColumn { column { cteColumn(relation, "userId") } }
            }
        }
    }

    @Test
    fun `relation composition carries nested parameters before consumer parameters`() {
        val relation = cte("filtered_users") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("user_id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("inner_id")
                            paramValue(11)
                        }
                    }
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(relation) } }
            .from { cte(relation.name) }
            .select { addColumn { column { cteColumn(relation, "user_id") } } }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("filtered_users", "user_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("outer_id")
                            paramValue(22)
                        }
                    }
                }
            }

        assertEquals(listOf("inner_id", "outer_id"), renderedParameterResult(query.ast).parameterOccurrences.map { it.name })
    }

    @Test
    fun `relation dependency renders inside dependent definition and alias reaches consumer`() {
        val base = cte("base_users") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("base_user_id")
                }
            }
        }
        val dependent = cte("selected_users") {
            withs { addWith { with(base) } }
            from { cte(base.name) }
            select {
                addColumn {
                    column { cteColumn(base, "base_user_id") }
                    alias("selected_user_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(dependent) } }
            .from { cte(dependent.name).alias("chosen") }
            .select { addColumn { column { cteColumn(dependent, "selected_user_id") } } }

        val sql = MySqlDialect().render(consumer.ast)!!
        assertFalse(Regex("\\bnull\\b", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertTrue(sql.indexOf("base_users AS").let { dependency ->
            dependency >= 0 && sql.indexOf("selected_users AS", dependency) > dependency
        })
        assertTrue(sql.contains("base_users.base_user_id"), "Relation B must consume Relation A's SQL output")
        assertTrue(sql.contains("chosen.selected_user_id"))
        val consumerSourceIndex = Regex("FROM\\s+selected_users", RegexOption.IGNORE_CASE).find(sql)?.range?.first ?: -1
        assertTrue(sql.indexOf("selected_users AS") < consumerSourceIndex)
        assertEquals(sql, MySqlDialect().render(consumer.ast), "Rendering must be deterministic")
    }

    @Test
    fun `three relation levels and consumer preserve dependency parameter order`() {
        val relationA = cte("a_relation") {
            from { table("users").alias("u") }
            select {
                addColumn { column { tableColumn("u", "id") }; alias("a_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValueParam {
                            paramName("a")
                            paramValue(1)
                        }
                    }
                }
            }
        }
        val relationB = cte("b_relation") {
            withs { addWith { with(relationA) } }
            from { cte(relationA.name) }
            select {
                addColumn { column { cteColumn(relationA, "a_id") }; alias("b_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("a_relation", "a_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("b")
                            paramValue(2)
                        }
                    }
                }
            }
        }
        val relationC = cte("c_relation") {
            withs { addWith { with(relationB) } }
            from { cte(relationB.name) }
            select {
                addColumn { column { cteColumn(relationB, "b_id") }; alias("c_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("b_relation", "b_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("c")
                            paramValue(3)
                        }
                    }
                }
            }
        }
        assertEquals(1, relationB.dependencies.size)
        assertSame(relationA, relationB.dependencies.single())
        assertEquals(listOf(relationA, relationB), relationC.dependencies)
        assertSame(relationA, relationC.dependencies[0])
        assertSame(relationB, relationC.dependencies[1])

        val consumer = QueryRenderSelectBuilder()
            .withs { addWith { with(relationC) } }
            .from { cte(relationC.name) }
            .select { addColumn { column { cteColumn(relationC, "c_id") } } }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("c_relation", "c_id") }
                        operationEqual()
                        sideValueParam {
                            paramName("d")
                            paramValue(4)
                        }
                    }
                }
            }

        val occurrences = renderedParameterResult(consumer.ast).parameterOccurrences
        assertEquals(listOf("a", "b", "c", "d"), occurrences.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), occurrences.map { it.value })
        assertEquals(listOf("a_relation", "b_relation", "c_relation"), consumer.ast.withs!!.withs.mapNotNull { it.metadata.withName })
    }

    private fun parameterIn(ast: IQueryRenderSelectAst): IQueryParameterAst<Any?> {
        val condition = ast.where!!.condition!!.conditions.single() as IQueryConditionsAst
        @Suppress("UNCHECKED_CAST")
        return assertIs<IQueryParameterAst<*>>(condition.conditionSideRight) as IQueryParameterAst<Any?>
    }

    @Test
    fun `relation instance identity is distinct from equal SQL names`() {
        val first = cte("same_relation_name") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }
        val second = cte("same_relation_name") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { cte(first) }.select {
                addColumn { column { cteColumn(second, "id") } }
            }
        }
    }

    @Test
    fun `withs and relation source compose in either call order`() {
        val relation = cte("call_order_relation") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        val sourceFirst = QueryRenderSelectBuilder()
            .from { cte(relation.name) }
            .withs {
                addWith { with("manual_cte") { select { addColumn { column { tableColumn("x", "id") } } } } }
                addWith { with(relation) }
            }
        val withsFirst = QueryRenderSelectBuilder()
            .withs {
                addWith { with("manual_cte") { select { addColumn { column { tableColumn("x", "id") } } } } }
                addWith { with(relation) }
            }
            .from { cte(relation.name) }

        for (query in listOf(sourceFirst, withsFirst)) {
            val names = query.ast.withs!!.withs.mapNotNull { it.metadata.withName }
            assertEquals(listOf("manual_cte", "call_order_relation"), names)
            assertTrue(MySqlDialect().render(query.ast)!!.contains("manual_cte AS"))
            assertTrue(MySqlDialect().render(query.ast)!!.contains("call_order_relation AS"))
        }
    }

    @Test
    fun `relation source and table source are mutually exclusive in both orders`() {
        val relation = cte("exclusive_relation") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { table("users").alias("u") }
                .from { cte(relation) }
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderSelectBuilder().from { cte(relation) }
                .from { table("users").alias("u") }
        }
    }

    @Test
    fun `duplicate explicit CTE and relation names are rendered for SQL validation`() {
        val relation = cte("duplicate_name") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        val queries = listOf(
            QueryRenderSelectBuilder()
                .withs {
                    addWith { with("duplicate_name") { select { addColumn { column { tableColumn("x", "id") } } } } }
                    addWith { with(relation) }
                }
                .from { cte("duplicate_name") },
            QueryRenderSelectBuilder()
                .from { cte("duplicate_name") }
                .withs {
                    addWith { with("duplicate_name") { select { addColumn { column { tableColumn("x", "id") } } } } }
                    addWith { with(relation) }
                },
        )

        for (query in queries) {
            assertEquals(listOf("duplicate_name", "duplicate_name"), query.ast.withs!!.withs.map { it.metadata.withName })
            assertTrue(MySqlDialect().render(query.ast)!!.contains("duplicate_name AS"))
        }
    }

    @Test
    fun `duplicate explicit CTE names are rendered without a relation source`() {
        val query = QueryRenderSelectBuilder().withs {
            addWith { with("same_name") { select { addColumn { column { tableColumn("x", "id") } } } } }
            addWith { with("same_name") { select { addColumn { column { tableColumn("y", "id") } } } } }
        }

        assertEquals(listOf("same_name", "same_name"), query.ast.withs!!.withs.map { it.metadata.withName })
        assertTrue(MySqlDialect().render(query.ast)!!.contains("same_name AS"))
    }

    @Test
    fun `cyclic relation dependency metadata reaches SQL rendering`() {
        val definitionRelation = cte("cycle_seed") {
            from { cte("cyclic_relation") }
            select { addColumn { column { tableAttribute("id") }; alias("id") } }
        }
        val cyclicDependencies = mutableListOf<Cte>()
        val cyclic = object : Cte by definitionRelation {
            override val name: String = "cyclic_relation"
            override val dependencies: List<Cte>
                get() = cyclicDependencies
        }
        cyclicDependencies += cyclic

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(cyclic) } }
            .from { cte(cyclic.name) }

        assertEquals(listOf("cyclic_relation"), query.ast.withs!!.withs.map { it.metadata.withName })
        assertTrue(MySqlDialect().render(query.ast)!!.contains("cyclic_relation"))
    }

    @Test
    fun `relations require non-empty unique aliases and reject unknown SQL output names`() {
        assertFailsWith<IllegalArgumentException> {
            cte("missing_alias") {
                from { table("users").alias("u") }
                select { addColumn { column { tableColumn("u", "id") } } }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            cte("model_default_is_not_explicit_alias") {
                from { model(RelationUserModel::class) }
                select { addColumn { column { tableSelector(RelationUserModel::class); columnSelector(RelationUserModel::class, RelationUserModel::id) } } }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            cte("blank_alias") {
                from { table("users").alias("u") }
                select { addColumn { column { tableColumn("u", "id") }; alias("") } }
            }
        }
        val relation = cte("user_ids") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("user_id") } }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { cte(relation) }.select {
                addColumn { column { cteColumn(relation, "missing_id") } }
            }
        }
    }

    @Test
    fun `relations reject duplicate SQL output aliases`() {
        assertFailsWith<IllegalArgumentException> {
            cte("duplicate_output_aliases") {
                from { table("users").alias("u") }
                select {
                    addColumn {
                        column { tableColumn("u", "id") }
                        alias("same")
                    }
                    addColumn {
                        column { tableColumn("u", "name") }
                        alias("same")
                    }
                }
            }
        }
    }

    @Test
    fun `aggregate renders as a SQL function call before its alias`() {
        val query = QueryRenderSelectBuilder()
            .select {
                addColumn {
                    methodCount {
                        addColumn {
                            tableSelector(RelationUserModel::class)
                            columnSelector(RelationUserModel::class, RelationUserModel::id)
                        }
                    }
                    alias("total")
                }
            }
            .from { model(RelationUserModel::class) }

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("count("))
        assertTrue(Regex("\\)\\s+As total").containsMatchIn(sql), sql)
    }
}
