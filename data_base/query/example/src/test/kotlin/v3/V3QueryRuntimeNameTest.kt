package gog.my_project.data_base.query.example.v3

import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.relations.UserDisplayQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPageQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPhoneQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V3QueryRuntimeNameTest {
    @Test
    fun `all v3 query declarations resolve runtime names from QBQuery and keep occurrence aliases`() {
        val statsDeclaration = UserStatsQueryV3()
        val phoneDeclaration = UserPhoneQueryV3()
        val pageDeclaration = UserPageQueryV3()
        val displayDeclaration = UserDisplayQueryV3()
        val stats = statsDeclaration.createCte(UserStatsQueryV3.RelationFilters())
        val phone = phoneDeclaration.createCte(UserPhoneQueryV3.RelationFilters())
        val page = pageDeclaration.createCte(UserPageQueryV3.RelationFilters())
        val display = displayDeclaration.createCte(UserDisplayQueryV3.RelationFilters())

        assertEquals("user_stats_query_v3", stats.name)
        assertEquals("user_phone_query_v3", phone.name)
        assertEquals("user_page_query_v3", page.name)
        assertEquals("user_display_query_v3", display.name)

        assertEquals("v3_user_stats", QueryRenderSelectBuilder().from { cte(stats) }.ast.table!!.metadata.aliasFromAst)
        assertEquals("v3_user_phone_details", QueryRenderSelectBuilder().from { cte(phone) }.ast.table!!.metadata.aliasFromAst)
        assertEquals("v3_user_page", QueryRenderSelectBuilder().from { cte(page) }.ast.table!!.metadata.aliasFromAst)
        assertEquals("v3_user_display", QueryRenderSelectBuilder().from { cte(display) }.ast.table!!.metadata.aliasFromAst)
        assertEquals(
            "page_occurrence",
            QueryRenderSelectBuilder().from {
                cte(page)
                alias("page_occurrence")
            }.ast.table!!.metadata.aliasFromAst,
        )
        val explicitAliasSource = QueryRenderSelectBuilder().from {
            cte(page)
            alias("page_occurrence")
        }.ast.table!!
        assertNull(explicitAliasSource.metadata.resolveName())
        assertEquals("page_occurrence", explicitAliasSource.metadata.aliasFromAst)
        assertTrue(explicitAliasSource.derivedQuery != null)

        val nestedPageQuery = QueryRenderSelectBuilder().from { cte(page) }.select {
            addColumn { column { tableSelector(UserPageQueryV3::class); columnSelector(UserPageQueryV3::id) } }
            addColumn { column { tableSelector(UserPageQueryV3::class); columnSelector(UserPageQueryV3::name) } }
            addColumn { column { tableSelector(UserPageQueryV3::class); columnSelector(UserPageQueryV3::family) } }
            addColumn { column { tableSelector(UserPageQueryV3::class); columnSelector(UserPageQueryV3::age) } }
        }
        assertEquals(null, nestedPageQuery.ast.withs)
        assertNull(nestedPageQuery.ast.table!!.metadata.resolveName())
        assertEquals("v3_user_page", nestedPageQuery.ast.table!!.metadata.aliasFromAst)
        val nestedPageDefinition = nestedPageQuery.ast.table!!.derivedQuery!!
        assertNull(nestedPageDefinition.table!!.metadata.resolveName())
        assertEquals("v3_user_stats", nestedPageDefinition.table!!.metadata.aliasFromAst)
        assertTrue(nestedPageDefinition.table!!.derivedQuery != null)
        assertEquals(
            listOf("v3_user_stats", "v3_user_stats", "v3_user_stats", "v3_user_stats"),
            page.definition.ast.select!!.columns.map { it.Column!!.metadata.resolveTableAlias() },
        )
        assertEquals(
            listOf("v3_user_page", "v3_user_page", "v3_user_page", "v3_user_page"),
            nestedPageQuery.ast.select!!.columns.map { it.Column!!.metadata.resolveTableAlias() },
        )
        assertEquals(
            listOf("id", "name", "family", "age"),
            nestedPageQuery.ast.select!!.columns.map { it.Column!!.metadata.resolveColumnName() },
        )
        val explicitlyAliasedQuery = QueryRenderSelectBuilder().from {
            cte(page)
            alias("page_occurrence")
        }.select {
            addColumn { column { tableSelector(UserPageQueryV3::class); columnSelector(UserPageQueryV3::id) } }
        }
        assertNull(explicitlyAliasedQuery.ast.table!!.metadata.resolveName())
        assertEquals("page_occurrence", explicitlyAliasedQuery.ast.table!!.metadata.aliasFromAst)
        assertTrue(explicitlyAliasedQuery.ast.table!!.derivedQuery != null)
        assertEquals("page_occurrence", explicitlyAliasedQuery.ast.select!!.columns.single().Column!!.metadata.resolveTableAlias())

        val displayQuery = QueryRenderSelectBuilder().from { cte(display) }.select {
            addColumn { column { tableSelector(UserDisplayQueryV3::class); columnSelector(UserDisplayQueryV3::id) } }
            addColumn { column { tableSelector(UserDisplayQueryV3::class); columnSelector(UserDisplayQueryV3::fullName) } }
            addColumn { column { tableSelector(UserDisplayQueryV3::class); columnSelector(UserDisplayQueryV3::age) } }
        }
        assertNull(displayQuery.ast.table!!.metadata.resolveName())
        assertEquals("v3_user_display", displayQuery.ast.table!!.metadata.aliasFromAst)
        assertTrue(displayQuery.ast.table!!.derivedQuery != null)
        assertEquals(listOf("id", "fullName", "age"), displayQuery.ast.select!!.columns.map { it.Column!!.metadata.resolveColumnName() })
        assertEquals(
            listOf("v3_user_display", "v3_user_display", "v3_user_display"),
            displayQuery.ast.select!!.columns.map { it.Column!!.metadata.resolveTableAlias() },
        )
    }
}
