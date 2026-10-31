package gog.my_project.data_base.migration.renderer.nodes.create_table.render_migration_create_table

import gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableAst
import gog.my_project.data_base.migration.dialect.data_class.create_table.column.MigrationColumnData
import gog.my_project.data_base.migration.dialect.data_class.create_table.render_migration_create_table.MigrationRenderCreateTableData
import gog.my_project.data_base.migration.dialect.data_class.create_table.table.MigrationTableData
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_table.render_migration_create_table.IMigrationRenderCreateTableCapability

class MySqlMigrationRenderCreateTableCapability : IMigrationRenderCreateTableCapability {

    override fun render(
        ast: IMigrationRenderCreateTableAst,
        ctx: IRenderContext,
        dataClass: MigrationRenderCreateTableData?
    ): String {
        val tableAst = requireNotNull(ast.migrationTableAst) { "CREATE TABLE requires a table name" }
        val tableName = requireNotNull(
            ctx.registry.render(tableAst, ctx.dialect, MigrationTableData())
        ) { "CREATE TABLE requires a table name" }

        val columns = ast.columns.map { column ->
            requireNotNull(ctx.registry.render(column, ctx.dialect, MigrationColumnData())) {
                "Renderer returned no SQL for column '${column.metadata.columnName}'"
            }
        }
        val definitions = ast.definitions.map { definition ->
            requireNotNull(ctx.registry.render(definition, ctx.dialect)) {
                "Renderer returned no SQL for CREATE TABLE definition '${definition::class.simpleName}'"
            }
        }
        val allDefinitions = columns + definitions
        require(allDefinitions.isNotEmpty()) { "CREATE TABLE '$tableName' requires at least one definition" }
        val ifNotExists = if (ast.metadata.ifNotExists) " IF NOT EXISTS" else ""
        return "${ctx.dialect._prefixCreateTable}$ifNotExists $tableName (\n  ${allDefinitions.joinToString(",\n  ")}\n)"
    }
}
