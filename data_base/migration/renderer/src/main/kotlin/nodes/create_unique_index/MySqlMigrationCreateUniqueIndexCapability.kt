package gog.my_project.data_base.migration.renderer.nodes.create_unique_index

import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IRenderContext
import gog.my_project.data_base.migration.dialect.nodes.create_unique_index.IMigrationCreateUniqueIndexCapability

class MySqlMigrationCreateUniqueIndexCapability : IMigrationCreateUniqueIndexCapability {
    override fun render(
        ast: IMigrationCreateUniqueIndexAst,
        ctx: IRenderContext,
        dataClass: MigrationDataClass?,
    ): String {
        val index = requireIdentifier(ast.metadata.indexName, "index")
        val table = requireIdentifier(ast.metadata.tableName, "table")
        val column = requireIdentifier(ast.metadata.columnName, "column")
        return "CREATE UNIQUE INDEX ${quoteIdentifier(index)} ON ${quoteIdentifier(table)} (${quoteIdentifier(column)})"
    }

    private fun requireIdentifier(value: String?, label: String): String =
        requireNotNull(value) { "CREATE UNIQUE INDEX requires a $label name" }

    private fun quoteIdentifier(value: String): String = "`${value.replace("`", "``")}`"
}
