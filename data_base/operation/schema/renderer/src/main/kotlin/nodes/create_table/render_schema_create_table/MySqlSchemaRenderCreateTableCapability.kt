package gog.my_project.data_base_schema.renderer.nodes.create_table.render_schema_create_table

import gog.my_project.data_base_schema.ast.interfaces.create_table.render_schema_create_table.ISchemaRenderCreateTableAst
import gog.my_project.data_base_schema.dialect.data_class.create_table.column.SchemaColumnData
import gog.my_project.data_base_schema.dialect.data_class.create_table.render_schema_create_table.SchemaRenderCreateTableData
import gog.my_project.data_base_schema.dialect.data_class.create_table.table.SchemaTableData
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.create_table.render_schema_create_table.ISchemaRenderCreateTableCapability

class MySqlSchemaRenderCreateTableCapability : ISchemaRenderCreateTableCapability {

    override fun render(
        ast: ISchemaRenderCreateTableAst,
        ctx: IRenderContext,
        dataClass: SchemaRenderCreateTableData?
    ): String {
        val tableAst = requireNotNull(ast.schemaTableAst) { "CREATE TABLE requires a table name" }
        val tableName = requireNotNull(
            ctx.registry.render(tableAst, ctx.dialect, SchemaTableData())
        ) { "CREATE TABLE requires a table name" }

        val columns = ast.columns.map { column ->
            requireNotNull(ctx.registry.render(column, ctx.dialect, SchemaColumnData())) {
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
