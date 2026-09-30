package gog.my_project.data_base.migration.builder.ast.rename_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.rename_column.IMigrationRenameColumnApi
import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst
import gog.my_project.data_base.migration.ast.schema.rename_column.MigrationRenameColumnAst

class MigrationRenameColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationRenameColumnAst = MigrationRenameColumnAst(),
) : IMigrationRenameColumnApi {
    override fun tableName(table: String): IMigrationRenameColumnApi {
        ast.tableName = table.trim().also { require(it.isNotEmpty()) { "RENAME COLUMN requires a non-blank table name" } }
        return this
    }
    override fun name(name: String): IMigrationRenameColumnApi {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "RENAME COLUMN requires a non-blank source column name" }
        require(ast.to == null || ast.to != normalized) { "RENAME COLUMN source and target names must differ" }
        ast.name = normalized
        return this
    }
    override fun to(name: String): IMigrationRenameColumnApi {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "RENAME COLUMN requires a non-blank target column name" }
        require(ast.name == null || ast.name != normalized) { "RENAME COLUMN source and target names must differ" }
        ast.to = normalized
        return this
    }
}
