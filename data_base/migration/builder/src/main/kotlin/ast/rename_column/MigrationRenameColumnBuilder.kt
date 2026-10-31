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
        ast.metadata.tableName = table.trim()
        return this
    }
    override fun name(name: String): IMigrationRenameColumnApi {
        ast.metadata.name = name.trim()
        return this
    }
    override fun to(name: String): IMigrationRenameColumnApi {
        ast.metadata.to = name.trim()
        return this
    }
}
