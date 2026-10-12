package gog.my_project.data_base.query.example.v3

import gog.my_project.data_base.query.builder.ast.select_builder.query_render_select.QueryRenderSelectBuilder
import gog.my_project.data_base.query.example.v3.queries.relations.UserDisplayQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPageQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserPhoneQueryV3
import gog.my_project.data_base.query.example.v3.queries.relations.UserStatsQueryV3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class V3QueryRuntimeNameTest {
    @Test
    fun `all v3 query declarations resolve runtime names from QBQuery and keep occurrence aliases`() {
        val statsDeclaration = UserStatsQueryV3(UserStatsQueryV3.RelationFilters())
        val phoneDeclaration = UserPhoneQueryV3(UserPhoneQueryV3.RelationFilters())
        val pageDeclaration = UserPageQueryV3(UserPageQueryV3.RelationFilters())
        val displayDeclaration = UserDisplayQueryV3(UserDisplayQueryV3.RelationFilters())
        val stats = statsDeclaration.createCte(statsDeclaration.params)
        val phone = phoneDeclaration.createCte(phoneDeclaration.params)
        val page = pageDeclaration.createCte(pageDeclaration.params)
        val display = displayDeclaration.createCte(displayDeclaration.params)

        assertEquals("user_stats_query_v3", stats.name)
        assertEquals("user_phone_query_v3", phone.name)
        assertEquals("user_page_query_v3", page.name)
        assertEquals("user_display_query_v3", display.name)

        assertEquals("v3_user_stats", QueryRenderSelectBuilder().from { cte(statsDeclaration) }.ast.table!!.tableAlias)
        assertEquals("v3_user_phone_details", QueryRenderSelectBuilder().from { cte(phoneDeclaration) }.ast.table!!.tableAlias)
        assertEquals("v3_user_page", QueryRenderSelectBuilder().from { cte(pageDeclaration) }.ast.table!!.tableAlias)
        assertEquals("v3_user_display", QueryRenderSelectBuilder().from { cte(displayDeclaration) }.ast.table!!.tableAlias)
        assertEquals(
            "page_occurrence",
            QueryRenderSelectBuilder().from {
                cte(pageDeclaration)
                alias("page_occurrence")
            }.ast.table!!.tableAlias,
        )
        val explicitAliasSource = QueryRenderSelectBuilder().from {
            cte(pageDeclaration)
            alias("page_occurrence")
        }.ast.table!!
        assertEquals(null, explicitAliasSource.cte)
        assertEquals("page_occurrence", explicitAliasSource.tableAlias)
        assertTrue(explicitAliasSource.derivedQuery != null)

        val nestedPageQuery = QueryRenderSelectBuilder().from { cte(pageDeclaration) }.select {
            addColumn { cteOutput(UserPageQueryV3::id) }
            addColumn { cteOutput(UserPageQueryV3::name) }
            addColumn { cteOutput(UserPageQueryV3::family) }
            addColumn { cteOutput(UserPageQueryV3::age) }
        }
        assertEquals(null, nestedPageQuery.ast.withs)
        assertEquals(null, nestedPageQuery.ast.table!!.cte)
        assertEquals("v3_user_page", nestedPageQuery.ast.table!!.tableAlias)
        val nestedPageDefinition = nestedPageQuery.ast.table!!.derivedQuery!!
        assertEquals(null, nestedPageDefinition.table!!.cte)
        assertEquals("v3_user_stats", nestedPageDefinition.table!!.tableAlias)
        assertTrue(nestedPageDefinition.table!!.derivedQuery != null)
        assertEquals(
            listOf("v3_user_stats", "v3_user_stats", "v3_user_stats", "v3_user_stats"),
            page.definition.ast.select!!.columns.map { it.Column!!.cteAlias },
        )
        assertEquals(
            listOf("v3_user_page", "v3_user_page", "v3_user_page", "v3_user_page"),
            nestedPageQuery.ast.select!!.columns.map { it.Column!!.cteAlias },
        )
        assertEquals(
            listOf("id", "name", "family", "age"),
            nestedPageQuery.ast.select!!.columns.map { it.Column!!.select },
        )
        val explicitlyAliasedQuery = QueryRenderSelectBuilder().from {
            cte(pageDeclaration)
            alias("page_occurrence")
        }.select {
            addColumn { cteOutput(UserPageQueryV3::id) }
        }
        assertEquals(null, explicitlyAliasedQuery.ast.table!!.cte)
        assertEquals("page_occurrence", explicitlyAliasedQuery.ast.table!!.tableAlias)
        assertTrue(explicitlyAliasedQuery.ast.table!!.derivedQuery != null)
        assertEquals("page_occurrence", explicitlyAliasedQuery.ast.select!!.columns.single().Column!!.cteAlias)

        val displayQuery = QueryRenderSelectBuilder().from { cte(displayDeclaration) }.select {
            addColumn { cteOutput(UserDisplayQueryV3::id) }
            addColumn { cteOutput(UserDisplayQueryV3::fullName) }
            addColumn { cteOutput(UserDisplayQueryV3::age) }
        }
        assertEquals(null, displayQuery.ast.table!!.cte)
        assertEquals("v3_user_display", displayQuery.ast.table!!.tableAlias)
        assertTrue(displayQuery.ast.table!!.derivedQuery != null)
        assertEquals(listOf("id", "fullName", "age"), displayQuery.ast.select!!.columns.map { it.Column!!.select })
        assertEquals(
            listOf("v3_user_display", "v3_user_display", "v3_user_display"),
            displayQuery.ast.select!!.columns.map { it.Column!!.cteAlias },
        )
    }
}
