package gog.my_project.data_base_schema.renderer.nodes.drop_column

import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.drop_column.ISchemaDropColumnCapability

class MySqlSchemaDropColumnCapability : ISchemaDropColumnCapability {
    override fun render(
        ast: ISchemaDropColumnAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val tableName = requireNotNull(ast.metadata.tableName) {
            "DROP COLUMN requires a table name"
        }
        val columnName = requireNotNull(ast.metadata.name) {
            "DROP COLUMN requires a column name"
        }

        return "ALTER TABLE ${quoteIdentifier(tableName)} DROP COLUMN ${quoteIdentifier(columnName)}"
    }

    private fun quoteIdentifier(identifier: String): String =
        "`${identifier.replace("`", "``")}`"
}
