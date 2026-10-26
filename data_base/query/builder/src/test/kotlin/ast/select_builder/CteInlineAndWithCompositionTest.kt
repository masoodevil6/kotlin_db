package gog.my_project.data_base.query.builder.ast.select_builder

import gog.my_project.data_base.query.api.interfaces.api.select_api.where.IQueryWhereApi
import gog.my_project.data_base.query.api.interfaces.relations.Cte
import gog.my_project.data_base.query.api.interfaces.relations.ICteDeclaration
import gog.my_project.data_base.query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base.query.builder.ast.select_builder.cte.cte
import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.renderer.dialects.MySqlDialect
import gog.my_project.data_base.query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base.query.renderer.parameters.QueryParameterRendering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CteInlineAndWithCompositionTest {

    @Test
    fun `inline Cte source and WITH-composed source retain their distinct AST and SQL forms`() {
        val composedRelation = dependentCteGraph()
        val composed = QueryRenderSelectBuilder()
            .withs { addWith { with(composedRelation) } }
            .from { cte(composedRelation.name).alias("composed_alias") }
            .select { addColumn { column { tableAttribute("1") }; alias("result") } }
            .where { addOuterParameter("outer_value", 33) }

        val inlineRelation = dependentCteGraph()
        val inline = QueryRenderSelectBuilder()
            .from { cte(inlineRelation).alias("inline_alias") }
            .select { addColumn { column { tableAttribute("1") }; alias("result") } }
            .where { addOuterParameter("outer_value", 33) }

        val composedTable = requireNotNull(composed.ast.table)
        val inlineTable = requireNotNull(inline.ast.table)
        val composedSql = render(composed.ast).displaySql.replace(Regex("\\s+"), " ").lowercase()
        val inlineSql = render(inline.ast).displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertEquals(listOf("base_runtime", "dependent_runtime"), composed.ast.withs!!.withs.map { it.metadata.withName })
        assertEquals("dependent_runtime", composedTable.metadata.nameFromAst)
        assertEquals("composed_alias", composedTable.metadata.aliasFromAst)
        assertNull(composedTable.derivedQuery)

        assertNull(inline.ast.withs)
        assertNotNull(inlineTable.derivedQuery)
        assertEquals("inline_alias", inlineTable.metadata.aliasFromAst)
        assertEquals("dependent_runtime", inlineTable.metadata.cteRuntimeName)
        assertEquals(listOf("base_runtime"), inlineTable.derivedQuery!!.withs!!.withs.map { it.metadata.withName })

        assertTrue(composedSql.contains("with base_runtime as"), composedSql)
        assertTrue(composedSql.contains("from dependent_runtime as composed_alias"), composedSql)
        assertTrue(inlineSql.contains("from ("), inlineSql)
        assertTrue(inlineSql.contains("base_runtime"), inlineSql)
        assertNotEquals(composedSql, inlineSql)

        assertEquals(listOf(11, 22, 33), render(composed.ast).parameterOccurrences.map { it.value })
        assertEquals(listOf(11, 22, 33), render(inline.ast).parameterOccurrences.map { it.value })
    }

    private fun dependentCteGraph(): Cte {
        val base = BaseCteDeclaration()(11)
        return DependentCteDeclaration(base)(22)
    }

    private fun IQueryWhereApi.addOuterParameter(name: String, value: Int) {
        conditions {
            addCondition {
                sideSelector { tableAttribute("1") }
                operationEqual()
                sideValueParam {
                    paramName(name)
                    paramValue(value)
                }
            }
        }
    }

    private fun render(ast: IQueryRenderSelectAst): IdentityAwareQueryRenderResult =
        assertIs<IdentityAwareQueryRenderResult>(QueryParameterRendering.render(ast, MySqlDialect()))
}

private class BaseCteDeclaration : ICteDeclaration<Int> {
    override fun createCte(params: Int): Cte = cte("base_runtime") {
        from { table("users").alias("u") }
        select {
            addColumn {
                column { tableColumn("u", "id") }
                alias("base_value")
            }
        }
        where {
            conditions {
                addCondition {
                    sideSelector { tableColumn("u", "id") }
                    operationEqual()
                    sideValueParam {
                        paramName("base_value")
                        paramValue(params)
                    }
                }
            }
        }
    }
}

private class DependentCteDeclaration(
    private val base: Cte,
) : ICteDeclaration<Int> {
    override fun createCte(params: Int): Cte = cte("dependent_runtime") {
        withs { addWith { with(base) } }
        from { cte(base.name).alias("base_source") }
        select {
            addColumn {
                column { cteColumn(base, "base_value") }
                alias("dependent_value")
            }
        }
        where {
            conditions {
                addCondition {
                    sideSelector { cteColumn(base, "base_value") }
                    operationGreaterThan()
                    sideValueParam {
                        paramName("dependent_value")
                        paramValue(params)
                    }
                }
            }
        }
    }
}
