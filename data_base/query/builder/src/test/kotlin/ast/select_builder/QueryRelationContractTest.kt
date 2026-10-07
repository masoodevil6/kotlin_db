package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBColumn
import gog.my_project.data_base.core.annotations.models.QBTable
import gog.my_project.data_base.core.managers.models.IModelBase
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.relations.queryRelation
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@QBTable(name = "relation_users", alias = "ru")
private class RelationUserModel : IModelBase {
    @QBColumn(name = "user_id", alias = "physical_user_id")
    val id: Long = 0
}

class QueryRelationContractTest {

    @Test
    fun `SQL aliases lower to existing CTE and CTE column nodes`() {
        val relation = queryRelation("user_stats") {
            table { table(RelationUserModel::class) }
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
            .from(relation, "stats")
            .select {
                addColumn { relationColumn(relation, "user_id") }
                addColumn { relationColumn(relation, "total") }
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
        val relation = queryRelation("aliased_stats") {
            table { table(RelationUserModel::class) }
            select {
                addColumn {
                    column(RelationUserModel::class, RelationUserModel::id)
                    alias("published_user_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .from(relation, "published")
            .select { addColumn { relationColumn(relation, "published_user_id") } }

        assertTrue(MySqlDialect().render(consumer.ast)!!.contains("published.published_user_id"))
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from(relation).select {
                addColumn { relationColumn(relation, "userId") }
            }
        }
    }

    @Test
    fun `relation composition carries nested parameters before consumer parameters`() {
        val relation = queryRelation("filtered_users") {
            table { table("users").alias("u") }
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
            .from(relation)
            .select { addColumn { relationColumn(relation, "user_id") } }
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
        val base = queryRelation("base_users") {
            table { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("base_user_id")
                }
            }
        }
        val dependent = queryRelation("selected_users") {
            from(base)
            select {
                addColumn {
                    relationColumn(base, "base_user_id")
                    alias("selected_user_id")
                }
            }
        }
        val consumer = QueryRenderSelectBuilder()
            .from(dependent, "chosen")
            .select { addColumn { relationColumn(dependent, "selected_user_id") } }

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
        val relationA = queryRelation("a_relation") {
            table { table("users").alias("u") }
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
        val relationB = queryRelation("b_relation") {
            from(relationA)
            select {
                addColumn { relationColumn(relationA, "a_id"); alias("b_id") }
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
        val relationC = queryRelation("c_relation") {
            from(relationB)
            select {
                addColumn { relationColumn(relationB, "b_id"); alias("c_id") }
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
        val consumer = QueryRenderSelectBuilder()
            .from(relationC)
            .select { addColumn { relationColumn(relationC, "c_id") } }
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
        val first = queryRelation("same_relation_name") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }
        val second = queryRelation("same_relation_name") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from(first).select {
                addColumn { relationColumn(second, "id") }
            }
        }
    }

    @Test
    fun `withs and relation source compose in either call order`() {
        val relation = queryRelation("call_order_relation") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        val sourceFirst = QueryRenderSelectBuilder()
            .from(relation)
            .withs { addWith { with("manual_cte") { select { addColumn { column { tableColumn("x", "id") } } } } } }
        val withsFirst = QueryRenderSelectBuilder()
            .withs { addWith { with("manual_cte") { select { addColumn { column { tableColumn("x", "id") } } } } } }
            .from(relation)

        for (query in listOf(sourceFirst, withsFirst)) {
            val names = query.ast.withs!!.withs.mapNotNull { it.withName }
            assertEquals(listOf("call_order_relation", "manual_cte"), names)
            assertTrue(MySqlDialect().render(query.ast)!!.contains("manual_cte AS"))
            assertTrue(MySqlDialect().render(query.ast)!!.contains("call_order_relation AS"))
        }
    }

    @Test
    fun `relation source and table source are mutually exclusive in both orders`() {
        val relation = queryRelation("exclusive_relation") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().table { table("users").alias("u") }.from(relation)
        }
        assertFailsWith<IllegalStateException> {
            QueryRenderSelectBuilder().from(relation).table { table("users").alias("u") }
        }
    }

    @Test
    fun `duplicate explicit CTE name and relation name are rejected`() {
        val relation = queryRelation("duplicate_name") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder()
                .withs { addWith { with("duplicate_name") { select { addColumn { column { tableColumn("x", "id") } } } } } }
                .from(relation)
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder()
                .from(relation)
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
        val definitionRelation = queryRelation("cycle_seed") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("id") } }
        }
        val cyclicDependencies = mutableListOf<QueryRelation>()
        val cyclic = object : QueryRelation by definitionRelation {
            override val name: String = "cyclic_relation"
            override val dependencies: List<QueryRelation>
                get() = cyclicDependencies
        }
        cyclicDependencies += cyclic

        assertFailsWith<IllegalStateException> {
            QueryRenderSelectBuilder().from(cyclic)
        }
    }

    @Test
    fun `relations require non-empty unique aliases and reject unknown SQL output names`() {
        assertFailsWith<IllegalArgumentException> {
            queryRelation("missing_alias") {
                table { table("users").alias("u") }
                select { addColumn { column { tableColumn("u", "id") } } }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            queryRelation("model_default_is_not_explicit_alias") {
                table { table(RelationUserModel::class) }
                select { addColumn { column(RelationUserModel::class, RelationUserModel::id) } }
            }
        }
        assertFailsWith<IllegalArgumentException> {
            queryRelation("blank_alias") {
                table { table("users").alias("u") }
                select { addColumn { column { tableColumn("u", "id") }; alias("") } }
            }
        }
        val relation = queryRelation("user_ids") {
            table { table("users").alias("u") }
            select { addColumn { column { tableColumn("u", "id") }; alias("user_id") } }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from(relation).select {
                addColumn { relationColumn(relation, "missing_id") }
            }
        }
    }

    @Test
    fun `relations reject duplicate SQL output aliases`() {
        assertFailsWith<IllegalArgumentException> {
            queryRelation("duplicate_output_aliases") {
                table { table("users").alias("u") }
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
            .table { table(RelationUserModel::class) }

        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("COUNT("))
        assertTrue(sql.contains(") As total"))
    }
}
