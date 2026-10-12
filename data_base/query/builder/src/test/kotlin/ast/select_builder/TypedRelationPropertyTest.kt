package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.fromCteInternal
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@QBQuery(name = "user_stats_runtime", alias = "stats_default")
private class UserStatsDeclaration(
    override val params: RelationFilters,
) : ICteDeclaration<UserStatsDeclaration.RelationFilters> {
    class RelationFilters

    val age: Int
        get() = error("Typed output property values must not be read")

    @QBQuerySelect(name = "display_name", alias = "display_name")
    val displayName: String
        get() = error("Typed output property values must not be read")

    override fun createCte(params: RelationFilters): Cte = buildCte {
        from { table("raw_user_stats").alias("raw") }
        select {
            addColumn {
                column { tableColumn("raw", "raw_age") }
                alias(UserStatsDeclaration::age)
            }
            addColumn {
                column { tableColumn("raw", "raw_display_name") }
                alias(UserStatsDeclaration::displayName)
            }
        }
    }
}

@QBQuery(name = "another_relation", alias = "another_relation")
private class OtherRelationDeclaration(
    override val params: RelationFilters,
) : ICteDeclaration<OtherRelationDeclaration.RelationFilters> {
    class RelationFilters

    val age: Int get() = error("Typed output property values must not be read")

    override fun createCte(params: RelationFilters): Cte = buildCte {
        from { table("raw_user_stats").alias("raw") }
        select {
            addColumn {
                column { tableColumn("raw", "age") }
                alias(OtherRelationDeclaration::age)
            }
        }
    }
}

class TypedRelationPropertyTest {

    private fun relationWithTypedAgeOutput(): Cte {
        val declaration = UserStatsDeclaration(UserStatsDeclaration.RelationFilters())
        return declaration.createCte(declaration.params)
    }

    private fun normalizeSql(sql: String): String = sql.replace(Regex("\\s+"), " ").lowercase()

    @Test
    fun `typed alias uses property name and does not evaluate output getter`() {
        val relation = relationWithTypedAgeOutput()
        val output = relation.definition.ast.select!!.columns.first()

        assertEquals("user_stats_runtime", relation.name)
        assertEquals(UserStatsDeclaration::class, (relation as BuiltCteComposition).declarationOwner)
        assertEquals("age", output.ColumnAlias)
        assertEquals(UserStatsDeclaration::age, output.PropertyReference)
        assertEquals(gog.my_project.data_base.query.ast.enums.DataType.INT, output.ExecutionType)
        assertTrue(MySqlDialect().render(relation.definition.ast)!!.contains("As age"))
    }

    @Test
    fun `typed relation columns use resolved default occurrence alias`() {
        val query = QueryRenderSelectBuilder()
            .fromCteInternal(relationWithTypedAgeOutput())
            .select {
                addColumn { cteOutput(UserStatsDeclaration::age) }
                addColumn { cteOutput(UserStatsDeclaration::displayName) }
            }

        assertEquals("user_stats_runtime", query.ast.table!!.cte)
        assertEquals("stats_default", query.ast.table!!.cteAlias)
        val outputs = query.ast.select!!.columns
        assertEquals(listOf("stats_default", "stats_default"), outputs.map { it.Column!!.cteAlias })
        assertEquals(listOf("age", "display_name"), outputs.map { it.Column!!.select })
        assertEquals(listOf("age", "display_name"), outputs.map { it.ColumnAlias })
        assertEquals(gog.my_project.data_base.query.ast.enums.DataType.INT, outputs.first().ExecutionType)
        assertEquals(UserStatsDeclaration::age, outputs.first().PropertyReference)
        assertEquals(UserStatsDeclaration::displayName, outputs.last().PropertyReference)
        val sql = MySqlDialect().render(query.ast)!!
        val normalizedSql = normalizeSql(sql)
        assertTrue("from user_stats_runtime as stats_default" in normalizedSql, sql)
        assertTrue(sql.contains("stats_default.age"), sql)
        assertTrue(sql.contains("stats_default.display_name"), sql)
        assertTrue(!sql.contains("user_stats_runtime.age"), sql)
    }

    @Test
    fun `typed relation column uses caller qualifier and preserves outer SQL alias`() {
        val query = QueryRenderSelectBuilder()
            .fromCteInternal(relationWithTypedAgeOutput(), "buyer")
            .select {
                addColumn {
                    cteOutput(UserStatsDeclaration::age, "buyer")
                    alias("buyer_age")
                }
            }

        val source = query.ast.select!!.columns.single()
        assertEquals("buyer", query.ast.table!!.cteAlias)
        assertEquals("buyer", source.Column!!.cteAlias)
        assertEquals("age", source.Column!!.select)
        assertEquals("buyer_age", source.ColumnAlias)
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("buyer.age"), sql)
        assertTrue(sql.contains("As buyer_age"), sql)
    }

    @Test
    fun `typed relation column without qualifier uses an explicit occurrence alias`() {
        val query = QueryRenderSelectBuilder()
            .fromCteInternal(relationWithTypedAgeOutput(), "buyer")
            .select {
                addColumn { cteOutput(UserStatsDeclaration::age) }
            }

        assertEquals("buyer", query.ast.table!!.cteAlias)
        assertEquals("buyer", query.ast.select!!.columns.single().Column!!.cteAlias)
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue("from user_stats_runtime as buyer" in normalizeSql(sql), sql)
        assertTrue(sql.contains("buyer.age"), sql)
        assertTrue(!sql.contains("user_stats_runtime.age"), sql)
    }

    @Test
    fun `typed alias rejects a property owned by a different declaration`() {
        assertFailsWith<IllegalArgumentException> {
            cte(
                name = "user_stats",
                declarationOwner = UserStatsDeclaration::class,
            ) {
                from { table("raw_user_stats").alias("raw") }
                select {
                    addColumn {
                        column { tableColumn("raw", "raw_age") }
                        alias(OtherRelationDeclaration::age)
                    }
                }
            }
        }
    }

    @Test
    fun `typed consumer rejects a relation whose owner differs from its property`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().fromCteInternal(
                OtherRelationDeclaration(OtherRelationDeclaration.RelationFilters())
                    .let { it.createCte(it.params) },
            ).select {
                addColumn { cteOutput(UserStatsDeclaration::age) }
            }
        }
    }

    @Test
    fun `typed consumer rejects an output that the relation did not publish`() {
        val declaration = UserStatsDeclaration(UserStatsDeclaration.RelationFilters())
        val relation = cte(
            name = "user_stats",
            declarationOwner = UserStatsDeclaration::class,
        ) {
            from { table("raw_user_stats").alias("raw") }
            select {
                addColumn {
                    column { tableColumn("raw", "raw_age") }
                    alias(UserStatsDeclaration::displayName)
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().fromCteInternal(relation).select {
                addColumn { cteOutput(UserStatsDeclaration::age) }
            }
        }
    }

    @Test
    fun `blank explicit factory identity is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            cte(
                name = " ",
                declarationOwner = UserStatsDeclaration::class,
            ) {}
        }
    }

    @Test
    fun `typed relation consumer alias uses the concrete property name`() {
        val query = QueryRenderSelectBuilder()
            .fromCteInternal(relationWithTypedAgeOutput())
            .select {
                addColumn {
                    cteOutput(UserStatsDeclaration::age)
                    alias(UserStatsDeclaration::age)
                }
            }

        assertEquals("age", query.ast.select!!.columns.single().ColumnAlias)
    }

    @Test
    fun `typed relation consumer rejects a blank explicit qualifier`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().fromCteInternal(relationWithTypedAgeOutput()).select {
                addColumn { cteOutput(UserStatsDeclaration::age, " ") }
            }
        }
    }
}
