package gog.my_project.data_base_migration.core.system_migration.cte

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
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
    fun `version CTE keeps its existing three output columns`() {
        val query = SystemMigrationVersionQuery()
        val rendered = render(SystemMigrationVersionQuery.RelationFilters())
        val ast = rendered.ast
        val sql = rendered.displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertEquals(SystemMigrationVersionModel.STATUS_MIGRATE_STARTED, query.status)
        assertEquals(listOf("id", "tagId", "status"), ast.select?.columns?.map { it.metadata.ColumnAlias })
        assertTrue(sql.contains("smv.status"), rendered.displaySql)
        assertTrue(sql.contains("as status"), rendered.displaySql)
        assertNull(query.message)
    }

    @Test
    fun `state CTE adds the persisted diagnostic message without changing the default CTE`() {
        val query = SystemMigrationVersionQuery()
        val cte = query.createStateCte()
        val ast = cte.definition.ast as IQueryRenderSelectAst
        val rendered = QueryParameterRendering.render(ast, MySqlDialect())
        val sql = when (rendered) {
            is IdentityAwareQueryRenderResult -> rendered.displaySql
            is LegacyQueryRenderResult -> requireNotNull(rendered.sql)
        }.replace(Regex("\\s+"), " ").lowercase()

        assertEquals(listOf("id", "tagId", "status", "message"), ast.select?.columns?.map { it.metadata.ColumnAlias })
        assertTrue(sql.contains("smv.message"), sql)
        assertTrue(sql.contains("as message"), sql)
    }

    @Test
    fun `incomplete state CTE excludes only completed status and binds its value`() {
        val cte = SystemMigrationVersionQuery().createStateCte(lastIncompleteVersion = true)
        val ast = cte.definition.ast as IQueryRenderSelectAst
        val rendered = QueryParameterRendering.render(ast, MySqlDialect())
        val sql = when (rendered) {
            is IdentityAwareQueryRenderResult -> rendered.displaySql
            is LegacyQueryRenderResult -> requireNotNull(rendered.sql)
        }.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(ast.where != null)
        assertTrue(
            Regex("smv\\.status\\s*(?:<>|!=)\\s*:system_migration_version_completed_status")
                .containsMatchIn(sql),
            sql,
        )
        assertTrue(sql.contains("smv.message"), sql)
        if (rendered is IdentityAwareQueryRenderResult) {
            assertEquals(listOf("system_migration_version_completed_status"), rendered.parameterOccurrences.map { it.name })
            assertEquals(
                listOf(SystemMigrationVersionModel.STATUS_MIGRATE_COMPLETED),
                rendered.parameterOccurrences.map { it.value },
            )
        }
    }

    @Test
    fun `state CTE can select one version id and binds that id`() {
        val cte = SystemMigrationVersionQuery().createStateCte(versionId = 41)
        val ast = cte.definition.ast as IQueryRenderSelectAst
        val rendered = QueryParameterRendering.render(ast, MySqlDialect())
        val sql = when (rendered) {
            is IdentityAwareQueryRenderResult -> rendered.displaySql
            is LegacyQueryRenderResult -> requireNotNull(rendered.sql)
        }.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(ast.where != null)
        assertTrue(Regex("smv\\.id\\s*=\\s*:system_migration_version_id").containsMatchIn(sql), sql)
        assertTrue(sql.contains("smv.message"), sql)
        if (rendered is IdentityAwareQueryRenderResult) {
            assertEquals(listOf("system_migration_version_id"), rendered.parameterOccurrences.map { it.name })
            assertEquals(listOf(41), rendered.parameterOccurrences.map { it.value })
        }
    }

    @Test
    fun `status alone filters versions`() {
        val rendered = render(
            SystemMigrationVersionQuery.RelationFilters(
                status = SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD,
            ),
        )
        val ast = rendered.ast
        val sql = rendered.displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(ast.where != null)
        assertTrue(sql.contains("smv.status = :system_migration_version_status"), rendered.displaySql)
        assertEquals(listOf("system_migration_version_status"), rendered.parameterNames)
        assertEquals(listOf(SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD), rendered.parameterValues)
    }

    @Test
    fun `id and status filters retain declaration order`() {
        val rendered = render(
            SystemMigrationVersionQuery.RelationFilters(
                id = 11,
                status = SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD,
            ),
        )

        assertEquals(
            listOf("system_migration_version_id", "system_migration_version_status"),
            rendered.parameterNames,
        )
        assertEquals(listOf(11, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD), rendered.parameterValues)
    }

    @Test
    fun `tag id and status filters retain declaration order`() {
        val rendered = render(
            SystemMigrationVersionQuery.RelationFilters(
                tagId = 23,
                status = SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD,
            ),
        )

        assertEquals(
            listOf("system_migration_version_tag_id", "system_migration_version_status"),
            rendered.parameterNames,
        )
        assertEquals(listOf(23, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD), rendered.parameterValues)
    }

    @Test
    fun `all version filters are rendered and bound in declaration order`() {
        val rendered = render(
            SystemMigrationVersionQuery.RelationFilters(
                id = 11,
                tagId = 23,
                status = SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD,
            ),
        )
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
        assertEquals(listOf(11, 23, SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD), rendered.parameterValues)
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
