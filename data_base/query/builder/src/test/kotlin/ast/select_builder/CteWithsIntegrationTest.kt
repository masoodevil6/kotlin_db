package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.core.annotations.models.QBQuery
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.builder.ast.select_builder.cte.CteDeclaration
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.fromCteInternal
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@QBQuery(name = "metadata_with_name", alias = "metadata_with_source")
private class MetadataNamedWithDeclaration : CteDeclaration<Unit>(Unit) {
    override fun createCte(params: Unit): Cte = buildCte {
        select { addColumn { column { tableAttribute("1") }; alias("value") } }
    }
}

class CteWithsIntegrationTest {

    @Test
    fun `with Cte uses its resolved declaration name and keeps its default source alias separate`() {
        val cte = MetadataNamedWithDeclaration().createCte(Unit)
        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(cte) } }
            .fromCteInternal(cte)
            .select { addColumn { cteOutput(cte, "value") } }

        val sql = MySqlDialect().render(query.ast)!!.replace(Regex("\\s+"), " ").lowercase()

        assertEquals("metadata_with_name", query.ast.withs!!.withs.single().withName)
        assertEquals("metadata_with_name", query.ast.table!!.cte)
        assertEquals("metadata_with_source", query.ast.table!!.cteAlias)
        assertTrue(sql.contains("metadata_with_name as"), sql)
        assertTrue(sql.contains("from metadata_with_name as metadata_with_source"), sql)
    }

    @Test
    fun `explicit WITH name overrides Cte runtime name without changing occurrence alias`() {
        val cte = cte("factory_runtime_name", MetadataNamedWithDeclaration::class) {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val defaultNamedQuery = QueryRenderSelectBuilder().withs {
            addWith { with(cte) }
        }
        assertEquals("factory_runtime_name", defaultNamedQuery.ast.withs!!.withs.single().withName)

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with("explicit_with_name", cte) } }
            .fromCteInternal(cte, "explicit_occurrence_alias")
            .select { addColumn { cteOutput(cte, "value") } }

        val sql = MySqlDialect().render(query.ast)!!.replace(Regex("\\s+"), " ").lowercase()

        assertEquals("explicit_with_name", query.ast.withs!!.withs.single().withName)
        assertEquals("explicit_with_name", query.ast.table!!.cte)
        assertEquals("explicit_occurrence_alias", query.ast.table!!.cteAlias)
        assertEquals("explicit_occurrence_alias", query.ast.select!!.columns.single().Column!!.cteAlias)
        assertEquals("factory_runtime_name", cte.name)
        assertTrue(sql.contains("explicit_with_name as"), sql)
        assertTrue(sql.contains("from explicit_with_name as explicit_occurrence_alias"), sql)
        assertTrue(!sql.contains("factory_runtime_name as"), sql)
    }

    @Test
    fun `explicit WITH name is used by dependent Cte source references`() {
        val base = cte("base_runtime_name") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val dependent = cte("dependent_runtime_name") {
            fromCteInternal(base)
            select { addColumn { cteOutput(base, "value"); alias("value") } }
        }

        val query = QueryRenderSelectBuilder().withs {
            addWith { with("renamed_base", base) }
            addWith { with(dependent) }
        }
        val withItems = query.ast.withs!!.withs
        val dependentBodyTable = withItems[1].withBody!!.table!!

        assertEquals(listOf("renamed_base", "dependent_runtime_name"), withItems.map { it.withName })
        assertEquals("renamed_base", dependentBodyTable.cte)
        assertEquals("base_runtime_name", dependentBodyTable.cteAlias)
    }

    @Test
    fun `explicit WITH name rejects blank values and duplicate effective names`() {
        val cte = cte("original_name") {
            select { addColumn { column { tableAttribute("1") }; alias("value") } }
        }
        val cteWithInferredName = cte("same_name") {
            select { addColumn { column { tableAttribute("2") }; alias("value") } }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().withs { addWith { with("", cte) } }
        }
        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().withs {
                addWith { with("same_name", cte) }
                addWith { with(cteWithInferredName) }
            }
        }
    }

    @Test
    fun `with accepts a Cte and composes dependencies and parameters in dependency-first order`() {
        val base = cte("with_base") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("base_id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("u", "id") }
                        operationEqual()
                        sideValue("base_value", 11)
                    }
                }
            }
        }
        val dependent = cte("with_dependent") {
            fromCteInternal(base)
            select {
                addColumn {
                    cteOutput(base, "base_id")
                    alias("dependent_id")
                }
            }
            where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableColumn("with_base", "base_id") }
                        operationEqual()
                        sideValue("dependent_value", 22)
                    }
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .withs { addWith { with(dependent) } }
            .select { addColumn { column { tableAttribute("1") }; alias("result") } }
            .from { table("outer_table") }
            .where {
                conditions {
                    addCondition {
                        logicalAnd()
                        sideSelector { tableAttribute("1") }
                        operationEqual()
                        sideValue("outer_value", 33)
                    }
                }
            }

        val names = query.ast.withs!!.withs.mapNotNull { it.withName }
        val sql = MySqlDialect().render(query.ast)!!

        assertEquals(listOf("with_base", "with_dependent"), names)
        assertEquals(listOf("base_value", "dependent_value", "outer_value"), query.params.map { it.name })
        assertEquals(listOf(11, 22, 33), query.params.map { it.value })
        assertTrue(sql.indexOf("with_base AS") < sql.indexOf("with_dependent AS"), sql)
        assertTrue(sql.indexOf(":base_value") < sql.indexOf(":dependent_value"), sql)
        assertTrue(sql.indexOf(":dependent_value") < sql.indexOf(":outer_value"), sql)
    }

    @Test
    fun `repeated Cte roots and the same source are emitted once by composition identity`() {
        val cte = cte("single_with_root") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
        }

        val query = QueryRenderSelectBuilder()
            .withs {
                addWith { with(cte) }
                addWith { with(cte) }
            }
            .fromCteInternal(cte, "source")
            .select { addColumn { cteOutput(cte, "id") } }

        assertEquals(listOf("single_with_root"), query.ast.withs!!.withs.mapNotNull { it.withName })
        assertEquals("source", query.ast.table!!.cteAlias)
        assertEquals("source", query.ast.select!!.columns.single().Column!!.cteAlias)
    }

    @Test
    fun `with rejects duplicate names between new Cte and legacy WITH items`() {
        val cte = cte("duplicate_with_name") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
        }

        assertFailsWith<IllegalArgumentException> {
            QueryRenderSelectBuilder().withs {
                addWith { with(cte) }
                addWith {
                    with("duplicate_with_name") {
                        select { addColumn { column { tableColumn("u", "id") } } }
                    }
                }
            }
        }
    }

    @Test
    fun `with rejects cyclic Cte dependencies`() {
        val seed = cte("with_cycle_seed") {
            from { table("users").alias("u") }
            select {
                addColumn {
                    column { tableColumn("u", "id") }
                    alias("id")
                }
            }
        }
        val cyclicDependencies = mutableListOf<Cte>()
        val cyclic = object : Cte by seed {
            override val name: String = "with_cycle"
            override val dependencies: List<Cte>
                get() = cyclicDependencies
        }
        cyclicDependencies += cyclic

        assertFailsWith<IllegalStateException> {
            QueryRenderSelectBuilder().withs { addWith { with(cyclic) } }
        }
    }
}
