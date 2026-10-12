package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.fromCteInternal
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class BoundValue(val value: Int)
private class InvocationCount(var value: Int = 0)

private class BoundParamQuery(
    override val params: BoundValue,
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
                        sideValue(parameterName, params.value)
                    }
                }
            }
        }
    }
}

private class DependentBoundParamQuery(
    override val params: BoundValue,
    private val dependency: BoundParamQuery,
    private val invocations: InvocationCount,
) : ICteDeclaration<BoundValue> {
    override fun createCte(params: BoundValue): Cte {
        invocations.value++
        return cte("parent_query") {
            from {
                cte(dependency)
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
                        sideValue("parent_value", params.value)
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
        val mutableParams: MutableList<gog.my_project.data_base.core.query.reader.SqlParameter<*>> = cte.definition.params
        assertTrue(mutableParams.isEmpty())
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
                        sideValue("cte_value", 1)
                    }
                }
            }
        }
        cte.definition.params[0] = SqlParameter.of("cte_value", 23)

        val consumer = QueryRenderSelectBuilder()
            .fromCteInternal(cte)
            .select { addColumn { cteOutput(cte, "id") } }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValue("outer_value", 31)
                    }
                }
            }

        val sql = MySqlDialect().render(consumer.ast)!!
        assertTrue(sql.indexOf(":cte_value") < sql.indexOf(":outer_value"), sql)
        assertEquals(listOf("cte_value", "outer_value"), consumer.params.map { it.name })
        assertEquals(listOf(23, 31), consumer.params.map { it.value })
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
            .fromCteInternal(cte)
            .select { addColumn { cteOutput(cte, "new_id") } }

        val body = consumer.ast.withs!!.withs.single().withBody!!
        val sql = MySqlDialect().render(consumer.ast)!!
        assertEquals("new_id", body.select!!.columns.single().ColumnAlias)
        assertTrue(sql.contains("new_source"), sql)
        assertTrue(sql.contains("As new_id"), sql)
    }

    @Test
    fun `consumer parameter list stays independent after CTE composition`() {
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
                        sideValue("cte_value", 5)
                    }
                }
            }
        }
        val originalParameter = cte.definition.params.single()
        val consumer = QueryRenderSelectBuilder()
            .fromCteInternal(cte)
            .select { addColumn { cteOutput(cte, "id") } }

        assertNotSame(cte.definition.params, consumer.params)
        assertSame(originalParameter, consumer.params.single())

        cte.definition.params.clear()
        cte.definition.params += SqlParameter.of("replacement_value", 99)

        assertEquals(listOf("cte_value"), consumer.params.map { it.name })
        assertEquals(listOf(5), consumer.params.map { it.value })
        assertSame(originalParameter, consumer.params.single())
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
            .fromCteInternal(cte)
            .select { addColumn { cteOutput(cte, "old_id") } }
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
            .fromCteInternal(cte)
            .select {
                addColumn {
                    column { tableAttribute("1") }
                    alias("constant")
                }
            }
        val composedBody = consumer.ast.withs!!.withs.single().withBody!!
        val definitionSelect = cte.definition.ast.select!!

        cte.definition.ast.select!!.columns.single().ColumnAlias = "updated_id"

        assertSame(definitionSelect, composedBody.select)
        assertEquals("updated_id", composedBody.select!!.columns.single().ColumnAlias)
        assertTrue(MySqlDialect().render(consumer.ast)!!.contains("As updated_id"))
    }

    @Test
    fun `model source uses the new model entry while preserving resolved table metadata`() {
        val query = QueryRenderSelectBuilder().from { model(RelationUserModel::class) }

        assertEquals("relation_users", query.ast.table!!.table)
        assertEquals("ru", query.ast.table!!.tableAlias)
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
                                sideValue("nested_id", 7)
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
                        sideValue("outer_id", 9)
                    }
                }
            }

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(Regex("FROM\\s+\\(\\s*SELECT", RegexOption.IGNORE_CASE).containsMatchIn(sql), sql)
        assertTrue(sql.contains("inner_users"), sql)
        assertTrue(sql.contains("As q"), sql)
        assertTrue(sql.indexOf(":nested_id") < sql.indexOf(":outer_id"), sql)
        assertEquals(listOf("nested_id", "outer_id"), query.params.map { it.name })
        assertEquals(listOf(7, 9), query.params.map { it.value })
    }

    @Test
    fun `inline declaration source reads bound parameters once and preserves nested parameter order`() {
        val childCalls = InvocationCount()
        val parentCalls = InvocationCount()
        val child = BoundParamQuery(BoundValue(17), "child_query", "child_value", childCalls)
        val parent = DependentBoundParamQuery(BoundValue(29), child, parentCalls)

        val query = QueryRenderSelectBuilder()
            .select { addColumn { column { tableAttribute("1") }; alias("result") } }
            .from {
                cte(parent)
                alias("parent_source")
            }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValue("outer_value", 41)
                    }
                }
            }

        val sql = MySqlDialect().render(query.ast)!!
        assertEquals(1, parentCalls.value)
        assertEquals(1, childCalls.value)
        assertEquals(null, query.ast.withs)
        assertEquals(null, query.ast.table!!.cte)
        assertEquals("parent_source", query.ast.table!!.tableAlias)
        val parentDefinition = query.ast.table!!.derivedQuery!!
        assertEquals(null, parentDefinition.table!!.cte)
        assertEquals("child_source", parentDefinition.table!!.tableAlias)
        assertTrue(parentDefinition.table!!.derivedQuery != null)
        assertEquals(listOf("child_value", "parent_value", "outer_value"), query.params.map { it.name })
        assertEquals(listOf(17, 29, 41), query.params.map { it.value })
        assertTrue(Regex("FROM\\s+\\(\\s*SELECT", RegexOption.IGNORE_CASE).findAll(sql).count() >= 2, sql)
        assertTrue(sql.contains("As child_source"), sql)
        assertTrue(sql.contains("As parent_source"), sql)
        assertTrue(!sql.contains("child_query AS"), sql)
        assertTrue(!sql.contains("parent_query AS"), sql)
        assertEquals(1, Regex(Regex.escape(":child_value")).findAll(sql).count(), sql)
        assertEquals(1, Regex(Regex.escape(":parent_value")).findAll(sql).count(), sql)
        assertEquals(1, Regex(Regex.escape(":outer_value")).findAll(sql).count(), sql)
        assertTrue(sql.indexOf(":child_value") < sql.indexOf(":parent_value"), sql)
        assertTrue(sql.indexOf(":parent_value") < sql.indexOf(":outer_value"), sql)
    }

    @Test
    fun `query-only table sources are rejected in CRUD and JOIN table contexts`() {
        val invocations = InvocationCount()
        val declaration = BoundParamQuery(BoundValue(3), "not_used", "not_used", invocations)

        assertFailsWith<IllegalStateException> {
            QueryRenderInsertBuilder().table { cte(declaration) }
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderUpdateBuilder().table { table { select { } } }
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderDeleteBuilder().table { cte(declaration) }
        }
        assertFailsWith<IllegalStateException> {
            QueryJoinsItemBuilder().table { table { select { } } }
        }
        assertEquals(0, invocations.value)
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
                        sideValue("id", 7)
                    }
                }
            }

        assertEquals("users", query.ast.table!!.table)
        assertEquals("u", query.ast.table!!.tableAlias)
        assertEquals(listOf("id"), query.params.map { it.name })
        assertEquals(listOf(7), query.params.map { it.value })

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
                    column(RelationUserModel::class, RelationUserModel::id)
                    alias("user_id")
                }
                addColumn {
                    column(RelationUserModel::class, RelationUserModel::id)
                    count()
                    alias("total")
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .fromCteInternal(relation, "stats")
            .select {
                addColumn { cteOutput(relation, "user_id") }
                addColumn { cteOutput(relation, "total") }
            }

        assertEquals("user_stats", query.ast.withs!!.withs.single().withName)
        assertEquals("user_stats", query.ast.table!!.cte)
        assertEquals("stats", query.ast.table!!.cteAlias)
        assertEquals("stats", query.ast.select!!.columns.first().Column!!.cteAlias)
        assertEquals("user_id", query.ast.select!!.columns.first().Column!!.select)
        assertEquals("total", query.ast.select!!.columns[1].Column!!.select)

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("user_stats AS"))
        assertTrue(sql.contains("stats.user_id"))
        assertTrue(sql.contains("stats.total"))
        assertTrue(sql.contains("As user_id"))
        assertTrue(sql.contains("COUNT("))
        assertTrue(sql.contains("As total"))
    }

    @Test
    fun `the SELECT alias is the exact name consumed by a relation`() {
        val relation = cte("aliased_stats") {
            from { model(RelationUserModel::class) }
            select {
                addColumn {
                    column(RelationUserModel::class, RelationUserModel::id)
                    alias("published_user_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .fromCteInternal(relation, "published")
            .select { addColumn { cteOutput(relation, "published_user_id") } }

        assertTrue(MySqlDialect().render(consumer.ast)!!.contains("published.published_user_id"))
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().fromCteInternal(relation).select {
                addColumn { cteOutput(relation, "userId") }
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
                        sideValue("inner_id", 11)
                    }
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .fromCteInternal(relation)
            .select { addColumn { cteOutput(relation, "user_id") } }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("filtered_users", "user_id") }
                        operationEqual()
                        sideValue("outer_id", 22)
                    }
                }
            }

        assertEquals(listOf("inner_id", "outer_id"), query.params.map { it.name })
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
            fromCteInternal(base)
            select {
                addColumn {
                    cteOutput(base, "base_user_id")
                    alias("selected_user_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .fromCteInternal(dependent, "chosen")
            .select { addColumn { cteOutput(dependent, "selected_user_id") } }

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
                        sideValue("a", 1)
                    }
                }
            }
        }
        val relationB = cte("b_relation") {
            fromCteInternal(relationA)
            select {
                addColumn { cteOutput(relationA, "a_id"); alias("b_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("a_relation", "a_id") }
                        operationEqual()
                        sideValue("b", 2)
                    }
                }
            }
        }
        val relationC = cte("c_relation") {
            fromCteInternal(relationB)
            select {
                addColumn { cteOutput(relationB, "b_id"); alias("c_id") }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("b_relation", "b_id") }
                        operationEqual()
                        sideValue("c", 3)
                    }
                }
            }
        }
        assertEquals(1, relationB.dependencies.size)
        assertSame(relationA, relationB.dependencies.single())
        assertEquals(1, relationC.dependencies.size)
        assertSame(relationB, relationC.dependencies.single())

        val consumer = QueryRenderSelectBuilder()
            .fromCteInternal(relationC)
            .select { addColumn { cteOutput(relationC, "c_id") } }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("c_relation", "c_id") }
                        operationEqual()
                        sideValue("d", 4)
                    }
                }
            }

        assertEquals(listOf("a", "b", "c", "d"), consumer.params.map { it.name })
        assertEquals(listOf(1, 2, 3, 4), consumer.params.map { it.value })
        assertEquals(listOf("a_relation", "b_relation", "c_relation"), consumer.ast.withs!!.withs.mapNotNull { it.withName })
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
            QueryRenderSelectBuilder().fromCteInternal(first).select {
                addColumn { cteOutput(second, "id") }
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
            .fromCteInternal(relation)
            .withs { addWith { with("manual_cte") { select { addColumn { column { tableColumn("x", "id") } } } } } }
        val withsFirst = QueryRenderSelectBuilder()
            .withs { addWith { with("manual_cte") { select { addColumn { column { tableColumn("x", "id") } } } } } }
            .fromCteInternal(relation)

        for (query in listOf(sourceFirst, withsFirst)) {
            val names = query.ast.withs!!.withs.mapNotNull { it.withName }
            assertEquals(listOf("call_order_relation", "manual_cte"), names)
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
                .fromCteInternal(relation)
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderSelectBuilder().fromCteInternal(relation)
                .from { table("users").alias("u") }
        }
    }

    @Test
    fun `duplicate explicit CTE name and relation name are rejected`() {
        val relation = cte("duplicate_name") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder()
                .withs { addWith { with("duplicate_name") { select { addColumn { column { tableColumn("x", "id") } } } } } }
                .fromCteInternal(relation)
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder()
                .fromCteInternal(relation)
                .withs { addWith { with("duplicate_name") { select { addColumn { column { tableColumn("x", "id") } } } } } }
        }
    }

    @Test
    fun `duplicate explicit CTE names are rejected without a relation source`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().withs {
                addWith { with("same_name") { select { addColumn { column { tableColumn("x", "id") } } } } }
                addWith { with("same_name") { select { addColumn { column { tableColumn("y", "id") } } } } }
            }
        }
    }

    @Test
    fun `cyclic relation dependency is rejected by the builder`() {
        val definitionRelation = cte("cycle_seed") {
            from { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }
        val cyclicDependencies = mutableListOf<Cte>()
        val cyclic = object : Cte by definitionRelation {
            override val name: String = "cyclic_relation"
            override val dependencies: List<Cte>
                get() = cyclicDependencies
        }
        cyclicDependencies += cyclic

        assertFailsWith<IllegalStateException> {
            QueryRenderSelectBuilder().fromCteInternal(cyclic)
        }
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
                select { addColumn { column(RelationUserModel::class, RelationUserModel::id) } }
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
            QueryRenderSelectBuilder().fromCteInternal(relation).select {
                addColumn { cteOutput(relation, "missing_id") }
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
                    column(RelationUserModel::class, RelationUserModel::id)
                    count()
                    alias("total")
                }
            }
            .from { model(RelationUserModel::class) }

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("COUNT("))
        assertTrue(sql.contains(") As total"))
    }
}
