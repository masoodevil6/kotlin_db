package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.api.interfaces.relations.IQueryRelation
import gog.my_project.data_base.query.api.interfaces.relations.QueryRelation
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.relations.BuiltQueryRelation
import gog.my_project.data_base.query.builder.relations.buildQueryRelation
import gog.my_project.data_base.query.builder.relations.queryRelation
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class UserStatsDeclaration(
    override val relationName: String = "user_stats",
) : IQueryRelation<UserStatsDeclaration.RelationFilters> {
    class RelationFilters

    val age: Int
        get() = error("Typed output property values must not be read")

    val displayName: String
        get() = error("Typed output property values must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation = buildQueryRelation {
        table { table("raw_user_stats").alias("raw") }
        select {
            addColumn {
                column { tableColumn("raw", "raw_age") }
                alias(UserStatsDeclaration::age)
            }
        }
    }
}

private class OtherRelationDeclaration : IQueryRelation<OtherRelationDeclaration.RelationFilters> {
    class RelationFilters

    override val relationName = "another_relation"
    val age: Int get() = error("Typed output property values must not be read")

    override fun queryRelation(params: RelationFilters): QueryRelation = buildQueryRelation {
        table { table("raw_user_stats").alias("raw") }
        select {
            addColumn {
                column { tableColumn("raw", "age") }
                alias(OtherRelationDeclaration::age)
            }
        }
    }
}

class TypedRelationPropertyTest {

    private fun relationWithTypedAgeOutput() = UserStatsDeclaration().queryRelation(UserStatsDeclaration.RelationFilters())

    @Test
    fun `typed alias uses property name and does not evaluate output getter`() {
        val relation = relationWithTypedAgeOutput()
        val output = relation.definition.ast.select!!.columns.single()

        assertEquals("user_stats", relation.name)
        assertEquals(UserStatsDeclaration::class, (relation as BuiltQueryRelation).declarationOwner)
        assertEquals("age", output.ColumnAlias)
        assertEquals(UserStatsDeclaration::age, output.PropertyReference)
        assertEquals(gog.my_project.data_base.query.ast.enums.DataType.INT, output.ExecutionType)
        assertTrue(MySqlDialect().render(relation.definition.ast)!!.contains("As age"))
    }

    @Test
    fun `typed relation column uses canonical relation name by default`() {
        val query = QueryRenderSelectBuilder()
            .from(relationWithTypedAgeOutput())
            .select {
                addColumn { relationColumn(UserStatsDeclaration::age) }
            }

        val source = query.ast.select!!.columns.single().Column!!
        val output = query.ast.select!!.columns.single()
        assertEquals("user_stats", query.ast.table!!.cte)
        assertEquals("user_stats", query.ast.table!!.cteAlias)
        assertEquals("user_stats", source.cteAlias)
        assertEquals("age", source.select)
        assertEquals("age", output.ColumnAlias)
        assertEquals(gog.my_project.data_base.query.ast.enums.DataType.INT, output.ExecutionType)
        assertEquals(UserStatsDeclaration::age, output.PropertyReference)
    }

    @Test
    fun `typed relation column uses caller qualifier and preserves outer SQL alias`() {
        val query = QueryRenderSelectBuilder()
            .from(relationWithTypedAgeOutput(), "buyer")
            .select {
                addColumn {
                    relationColumn(UserStatsDeclaration::age, "buyer")
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
    fun `missing qualifier uses canonical name without inferring the source alias`() {
        val query = QueryRenderSelectBuilder()
            .from(relationWithTypedAgeOutput(), "buyer")
            .select {
                addColumn { relationColumn(UserStatsDeclaration::age) }
            }

        assertEquals("buyer", query.ast.table!!.cteAlias)
        assertEquals("user_stats", query.ast.select!!.columns.single().Column!!.cteAlias)
    }

    @Test
    fun `typed alias rejects a property owned by a different declaration`() {
        assertFailsWith<IllegalArgumentException> {
            queryRelation(
                name = "user_stats",
                declarationOwner = UserStatsDeclaration::class,
            ) {
                table { table("raw_user_stats").alias("raw") }
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
            QueryRenderSelectBuilder().from(OtherRelationDeclaration().queryRelation(OtherRelationDeclaration.RelationFilters())).select {
                addColumn { relationColumn(UserStatsDeclaration::age) }
            }
        }
    }

    @Test
    fun `typed consumer rejects an output that the relation did not publish`() {
        val declaration = UserStatsDeclaration()
        val relation = queryRelation(
            name = declaration.relationName,
            declarationOwner = UserStatsDeclaration::class,
        ) {
            table { table("raw_user_stats").alias("raw") }
            select {
                addColumn {
                    column { tableColumn("raw", "raw_age") }
                    alias(UserStatsDeclaration::displayName)
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from(relation).select {
                addColumn { relationColumn(UserStatsDeclaration::age) }
            }
        }
    }

    @Test
    fun `blank relation identity is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            UserStatsDeclaration(" ").queryRelation(UserStatsDeclaration.RelationFilters())
        }
    }

    @Test
    fun `typed relation consumer alias uses the concrete property name`() {
        val query = QueryRenderSelectBuilder()
            .from(relationWithTypedAgeOutput())
            .select {
                addColumn {
                    relationColumn(UserStatsDeclaration::age)
                    alias(UserStatsDeclaration::age)
                }
            }

        assertEquals("age", query.ast.select!!.columns.single().ColumnAlias)
    }

    @Test
    fun `typed relation consumer rejects a blank explicit qualifier`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from(relationWithTypedAgeOutput()).select {
                addColumn { relationColumn(UserStatsDeclaration::age, " ") }
            }
        }
    }
}
