package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.core.annotations.models.QBQuerySelect
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.cte.BuiltCteComposition
import gog.my_project.data_base.query.builder.ast.select_builder.cte.buildCte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@QBQuery(name = "user_stats_runtime", alias = "stats_default")
private class UserStatsDeclaration : ICteDeclaration<UserStatsDeclaration.RelationFilters> {
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
private class OtherRelationDeclaration : ICteDeclaration<OtherRelationDeclaration.RelationFilters> {
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
        val declaration = UserStatsDeclaration()
        return declaration.createCte(UserStatsDeclaration.RelationFilters())
    }

    private fun normalizeSql(sql: String): String = sql.replace(Regex("\\s+"), " ").lowercase()

    @Test
    fun `typed alias uses property name and does not evaluate output getter`() {
        val relation = relationWithTypedAgeOutput()
        val output = relation.definition.ast.select!!.columns.first()

        assertEquals("user_stats_runtime", relation.name)
        assertEquals(UserStatsDeclaration::class, (relation as BuiltCteComposition).declarationOwner)
        assertEquals("age", output.metadata.ColumnAlias)
        assertEquals(UserStatsDeclaration::age, output.metadata.PropertyReference)
        assertEquals(gog.my_project.data_base.query.ast.enums.DataType.INT, output.metadata.ExecutionType)
        assertTrue(MySqlDialect().render(relation.definition.ast)!!.contains("As age"))
    }

    @Test
    fun `typed relation columns use resolved default occurrence alias`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(relationWithTypedAgeOutput()) }
            .select {
                addColumn {
                    column {
                        tableSelector(UserStatsDeclaration::class)
                        columnSelector(UserStatsDeclaration::age)
                    }
                }
                addColumn {
                    column {
                        tableSelector(UserStatsDeclaration::class)
                        columnSelector(UserStatsDeclaration::displayName)
                    }
                }
            }

        assertEquals("user_stats_runtime", query.ast.table!!.metadata.cteRuntimeName)
        assertEquals("stats_default", query.ast.table!!.metadata.aliasFromAst)
        val outputs = query.ast.select!!.columns
        assertEquals(listOf("stats_default", "stats_default"), outputs.map { it.Column!!.metadata.resolveTableAlias() })
        assertEquals(listOf("age", "display_name"), outputs.map { it.Column!!.metadata.resolveColumnName() })
        assertEquals("user_stats_declaration", outputs.first().Column!!.metadata.tableAliasFromModel)
        assertEquals("stats_default", outputs.first().Column!!.metadata.tableAliasFromAnnotation)
        assertNull(outputs.first().Column!!.metadata.tableAliasFromAst)
        assertEquals("age", outputs.first().Column!!.metadata.columnNameFromModel)
        assertNull(outputs.first().Column!!.metadata.columnNameFromAnnotation)
        assertEquals("age", outputs.first().Column!!.metadata.columnNameFromAst)
        assertEquals("displayName", outputs.last().Column!!.metadata.columnNameFromModel)
        assertEquals("display_name", outputs.last().Column!!.metadata.columnNameFromAnnotation)
        assertEquals("display_name", outputs.last().Column!!.metadata.columnNameFromAst)
        assertEquals("display_name", outputs.last().Column!!.metadata.resolveColumnAlias())
        assertEquals(listOf("age", "display_name"), outputs.map { it.metadata.ColumnAlias })
        assertEquals(gog.my_project.data_base.query.ast.enums.DataType.INT, outputs.first().metadata.ExecutionType)
        assertEquals(UserStatsDeclaration::age, outputs.first().metadata.PropertyReference)
        assertEquals(UserStatsDeclaration::displayName, outputs.last().metadata.PropertyReference)
        val sql = MySqlDialect().render(query.ast)!!
        val normalizedSql = normalizeSql(sql)
        assertTrue("from (" in normalizedSql && ") as stats_default" in normalizedSql, sql)
        assertTrue(sql.contains("stats_default.age"), sql)
        assertTrue(sql.contains("stats_default.display_name"), sql)
        assertTrue(!sql.contains("user_stats_runtime.age"), sql)
    }

    @Test
    fun `typed relation source preserves explicit occurrence alias`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(relationWithTypedAgeOutput()).alias("buyer") }
            .select {
                addColumn {
                    column {
                        tableSelector(UserStatsDeclaration::class)
                        columnSelector(UserStatsDeclaration::age)
                    }
                    alias("buyer_age")
                }
            }

        val source = query.ast.select!!.columns.single()
        assertEquals("buyer", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("buyer", source.Column!!.metadata.resolveTableAlias())
        assertEquals("buyer", source.Column!!.metadata.tableAliasFromAst)
        assertEquals("stats_default", source.Column!!.metadata.tableAliasFromAnnotation)
        assertEquals("age", source.Column!!.metadata.resolveColumnName())
        assertEquals("buyer_age", source.metadata.ColumnAlias)
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue(sql.contains("buyer.age"), sql)
        assertTrue(sql.contains("As buyer_age"), sql)
    }

    @Test
    fun `typed relation column without qualifier uses an explicit occurrence alias`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(relationWithTypedAgeOutput()).alias("buyer") }
            .select {
                addColumn {
                    column {
                        tableSelector(UserStatsDeclaration::class)
                        columnSelector(UserStatsDeclaration::age)
                    }
                }
            }

        assertEquals("buyer", query.ast.table!!.metadata.aliasFromAst)
        assertEquals("buyer", query.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())
        assertEquals("age", query.ast.select!!.columns.single().Column!!.metadata.resolveColumnName())
        val sql = MySqlDialect().render(query.ast)!!
        assertTrue("from (" in normalizeSql(sql) && ") as buyer" in normalizeSql(sql), sql)
        assertTrue(sql.contains("buyer.age"), sql)
        assertTrue(!sql.contains("user_stats_runtime.age"), sql)
    }

    @Test
    fun `string-selected source keeps its alias with typed output metadata`() {
        val query = QueryRenderSelectBuilder()
            .from { cte(relationWithTypedAgeOutput()).alias("buyer") }
            .select {
                addColumn {
                    column {
                        tableSelector("buyer")
                        columnSelector(UserStatsDeclaration::class, UserStatsDeclaration::age)
                    }
                    alias("buyer_age")
                }
            }

        val selected = query.ast.select!!.columns.single().Column!!
        assertEquals("buyer", selected.metadata.resolveTableAlias())
        assertEquals("age", selected.metadata.resolveColumnName())
        assertTrue(MySqlDialect().render(query.ast)!!.contains("buyer.age"))
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
            QueryRenderSelectBuilder().from {
                cte(
                    OtherRelationDeclaration().createCte(OtherRelationDeclaration.RelationFilters()),
                )
            }.select {
                addColumn { column { tableSelector(UserStatsDeclaration::class); columnSelector(UserStatsDeclaration::age) } }
            }
        }
    }

    @Test
    fun `typed consumer rejects an output that the relation did not publish`() {
        val declaration = UserStatsDeclaration()
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
            QueryRenderSelectBuilder().from { cte(relation) }.select {
                addColumn { column { tableSelector(UserStatsDeclaration::class); columnSelector(UserStatsDeclaration::age) } }
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
            .from { cte(relationWithTypedAgeOutput()) }
            .select {
                addColumn {
                    column { tableSelector(UserStatsDeclaration::class); columnSelector(UserStatsDeclaration::age) }
                    alias(UserStatsDeclaration::age)
                }
            }

        assertEquals("age", query.ast.select!!.columns.single().metadata.ColumnAlias)
    }

    @Test
    fun `typed relation source rejects a blank occurrence alias`() {
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().from { cte(relationWithTypedAgeOutput()).alias(" ") }
        }
    }
}
