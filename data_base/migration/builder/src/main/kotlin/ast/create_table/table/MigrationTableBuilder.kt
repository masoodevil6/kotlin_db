package gog.my_project.data_base.migration.builder.ast.create_table.table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_table.table.IMigrationTableApi
import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.ast.schema.create_table.table.MigrationTableAst

class MigrationTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IMigrationTableAst = MigrationTableAst()
) : IMigrationTableApi {

    override fun tableName(table: String): IMigrationTableApi {
        this.ast.tableName = table.trim();
        return this;
    }

}
