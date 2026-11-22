package gog.my_project.data_base_migration.core.system_migration.query

import gog.my_project.data_base_migration.core.system_migration.model.SystemMigrationVersionModel
import gog.my_project.data_base_query.renderer.dialects.MySqlDialect
import gog.my_project.data_base_query.renderer.parameters.IdentityAwareQueryRenderResult
import gog.my_project.data_base_query.renderer.parameters.QueryParameterRendering
import java.sql.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SystemMigrationVersionUpdateQueryTest {
    @Test
    fun `update targets version status by id and binds values in query order`() {
        val query = SystemMigrationVersionUpdateQuery(
            id = 37,
            status = SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD,
        ).query()
        val ast = query.ast

        assertEquals("system_migration_version", ast.table?.metadata?.resolveName())
        assertEquals("smv", ast.table?.metadata?.resolveAlias())
        assertEquals(1, ast.columns.size)
        assertEquals("status", ast.columns.single().metadata.columnName)
        assertEquals("smv", ast.columns.single().metadata.columnAlias)
        assertEquals("status", ast.columns.single().parameterAst?.metadata?.name)
        assertEquals(SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD, ast.columns.single().parameterAst?.metadata?.value)
        assertEquals(Types.INTEGER, ast.columns.single().parameterAst?.metadata?.sqlType)
        assertTrue(ast.where?.condition?.conditions?.isNotEmpty() == true)

        val rendered = assertIs<IdentityAwareQueryRenderResult>(
            QueryParameterRendering.renderDml(ast, MySqlDialect()),
        )
        val sql = rendered.displaySql.replace(Regex("\\s+"), " ").lowercase()

        assertTrue(sql.contains("update system_migration_version"), rendered.displaySql)
        assertTrue(sql.contains("smv.status = :status"), rendered.displaySql)
        assertTrue(
            sql.contains("where") &&
                sql.contains("smv.id = :id") &&
                sql.indexOf("where") < sql.indexOf("smv.id = :id"),
            rendered.displaySql,
        )
        assertEquals(listOf("status", "id"), rendered.parameterOccurrences.map { it.name })
        assertEquals(listOf(SystemMigrationVersionModel.STATUS_MIGRATE_FAILED_RECORD, 37), rendered.parameterOccurrences.map { it.value })
        assertEquals(listOf(Types.INTEGER, Types.INTEGER), rendered.parameterOccurrences.map { it.sqlType })
        assertEquals(2, rendered.jdbcSql.count { it == '?' })
    }
}
