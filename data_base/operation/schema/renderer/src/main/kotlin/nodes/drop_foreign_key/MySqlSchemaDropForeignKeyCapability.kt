package gog.my_project.data_base_schema.renderer.nodes.drop_foreign_key

import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.nodes.drop_foreign_key.ISchemaDropForeignKeyCapability

class MySqlSchemaDropForeignKeyCapability internal constructor(
    private val supportsIfExists: Boolean,
) : ISchemaDropForeignKeyCapability {
    constructor() : this(supportsIfExists = false)

    override fun render(
        ast: ISchemaDropForeignKeyAst,
        ctx: IRenderContext,
        dataClass: SchemaDataClass?,
    ): String {
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val constraint = requireIdentifier(ast.metadata.constraintName, "constraint")
        val ifExists = ast.ifExistAst?.let { child ->
            val fragment = requireNotNull(ctx.registry.render(child, ctx.dialect)) {
                "Renderer returned no SQL for DROP FOREIGN KEY IF EXISTS"
            }
            if (!supportsIfExists) {
                throw UnsupportedOperationException("DROP FOREIGN KEY IF EXISTS is not supported by this SQL dialect")
            }
            " $fragment"
        }.orEmpty()
        return "ALTER TABLE ${quoteIdentifier(table)} DROP FOREIGN KEY$ifExists ${quoteIdentifier(constraint)}"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "DROP FOREIGN KEY requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
