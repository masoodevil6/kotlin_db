package gog.my_project.data_base.migration.builder.ast.drop_table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.drop_table.IMigrationDropTableApi
import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst
import gog.my_project.data_base.migration.ast.schema.drop_table.MigrationDropTableAst

class MigrationDropTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationDropTableAst = MigrationDropTableAst(),
) : IMigrationDropTableApi {

    override fun tableName(table: String): IMigrationDropTableApi {
        ast.tableName = table.trim().also {
            require(it.isNotEmpty()) { "DROP TABLE requires a non-blank table name" }
        }
        return this
    }

    override fun ifExists(): IMigrationDropTableApi {
        ast.ifExists = true
        return this
    }
}