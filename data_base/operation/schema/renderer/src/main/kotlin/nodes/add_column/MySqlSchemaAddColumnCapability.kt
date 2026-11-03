package gog.my_project.data_base_schema.renderer.nodes.add_column

import gog.my_project.data_base_schema.ast.interfaces.add_column.ISchemaAddColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.data_class.create_table.column.SchemaColumnData
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.add_column.ISchemaAddColumnCapability

class MySqlSchemaAddColumnCapability : ISchemaAddColumnCapability {
    override fun render(
        ast: ISchemaAddColumnAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val tableName = requireNotNull(ast.metadata.tableName) {
            "ADD COLUMN requires a table name"
        }
        val column = requireNotNull(ast.columnAst) {
            "ADD COLUMN requires a column definition"
        }
        val columnName = requireNotNull(column.metadata.columnName) {
            "ADD COLUMN requires a column name"
        }
        val type = requireNotNull(column.metadata.columnDataType) {
            "Column '$columnName' must have a data type"
        }

        val columnSql = requireNotNull(
            ctx.registry.render(column, ctx.dialect, SchemaColumnData()),
        ) { "Renderer returned no SQL for column '$columnName'" }

        return "ALTER TABLE ${quoteIdentifier(tableName)} ADD COLUMN $columnSql"
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}
