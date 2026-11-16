package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base_query.ast.interfaces.select_interface.query_render_select.IQueryRenderSelectAst
import gog.my_project.data_base_query.renderer.dialects.MySqlDialect
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.LegacyQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemMigrationVersionQueryTest {
    @Test
    fun `version CTE selects status output`() {
        val query = SystemMigrationVersionQuery()
        val rendered = render(SystemMigrationVersionQuery.RelationFilters())
        val ast = rendered.ast
        val sql = rendered.displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertEquals(0, query.status)
        assertEquals(listOf("id", "tagId", "status"), ast.select?.columns?.map { it.metadata.ColumnAlias })
        assertTrue(sql.contains("smv.status"), rendered.displaySql)
        assertTrue(sql.contains("as status"), rendered.displaySql)
    }

    @Test
    fun `status alone filters versions`() {
        val rendered = render(SystemMigrationVersionQuery.RelationFilters(status = 2))
        val ast = rendered.ast
        val sql = rendered.displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(ast.where != null)
        assertTrue(sql.contains("smv.status = :system_migration_version_status"), rendered.displaySql)
        assertEquals(listOf("system_migration_version_status"), rendered.parameterNames)
        assertEquals(listOf(2), rendered.parameterValues)
    }

    @Test
    fun `id and status filters retain declaration order`() {
        val rendered = render(SystemMigrationVersionQuery.RelationFilters(id = 11, status = 2))

        assertEquals(
            listOf("system_migration_version_id", "system_migration_version_status"),
            rendered.parameterNames,
        )
        assertEquals(listOf(11, 2), rendered.parameterValues)
    }

    @Test
    fun `tag id and status filters retain declaration order`() {
        val rendered = render(SystemMigrationVersionQuery.RelationFilters(tagId = 23, status = 2))

        assertEquals(
            listOf("system_migration_version_tag_id", "system_migration_version_status"),
            rendered.parameterNames,
        )
        assertEquals(listOf(23, 2), rendered.parameterValues)
    }

    @Test
    fun `all version filters are rendered and bound in declaration order`() {
        val rendered = render(SystemMigrationVersionQuery.RelationFilters(id = 11, tagId = 23, status = 2))
        val ast = rendered.ast
        val sql = rendered.displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(ast.where != null)
        assertTrue(sql.contains("smv.id = :system_migration_version_id"), rendered.displaySql)
        assertTrue(sql.contains("smv.tag_id = :system_migration_version_tag_id"), rendered.displaySql)
        assertTrue(sql.contains("smv.status = :system_migration_version_status"), rendered.displaySql)
        assertEquals(
            listOf(
                "system_migration_version_id",
                "system_migration_version_tag_id",
                "system_migration_version_status",
            ),
            rendered.parameterNames,
        )
        assertEquals(listOf(11, 23, 2), rendered.parameterValues)
    }

    @Test
    fun `where is omitted when all version filters are null`() {
        val rendered = render(SystemMigrationVersionQuery.RelationFilters())
        val ast = rendered.ast
        val sql = rendered.displaySql.lowercase()

        assertNull(ast.where)
        assertTrue(!sql.contains(" where "), rendered.displaySql)
        assertEquals(emptyList(), rendered.parameterNames)
        assertEquals(emptyList(), rendered.parameterValues)
    }

    private fun render(filters: SystemMigrationVersionQuery.RelationFilters): RenderedVersionQuery {
        val cte = SystemMigrationVersionQuery().createCte(filters)
        val ast = cte.definition.ast as IQueryRenderSelectAst
        val rendered = QueryParameterRendering.render(ast, MySqlDialect())
        return when (rendered) {
            is IdentityAwareQueryRenderResult -> RenderedVersionQuery(
                ast,
                rendered.displaySql,
                rendered.parameterOccurrences.map { it.name },
                rendered.parameterOccurrences.map { it.value },
            )
            is LegacyQueryRenderResult -> RenderedVersionQuery(
                ast,
                requireNotNull(rendered.sql),
                emptyList(),
                emptyList(),
            )
        }
    }

    private data class RenderedVersionQuery(
        val ast: IQueryRenderSelectAst,
        val displaySql: String,
        val parameterNames: List<String>,
        val parameterValues: List<Any?>,
    ) {
    }
}
