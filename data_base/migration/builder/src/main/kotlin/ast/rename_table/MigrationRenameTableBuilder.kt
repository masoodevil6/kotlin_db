package gog.my_project.data_base.migration.builder.ast.rename_table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.rename_table.IMigrationRenameTableApi
import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst
import gog.my_project.data_base.migration.ast.schema.rename_table.MigrationRenameTableAst

class MigrationRenameTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationRenameTableAst = MigrationRenameTableAst(),
) : IMigrationRenameTableApi {

    override fun fromTableName(table: String): IMigrationRenameTableApi {
        ast.metadata.fromTableName = table.trim()
        return this
    }

    override fun toTableName(table: String): IMigrationRenameTableApi {
        ast.metadata.toTableName = table.trim()
        return this
    }
}
