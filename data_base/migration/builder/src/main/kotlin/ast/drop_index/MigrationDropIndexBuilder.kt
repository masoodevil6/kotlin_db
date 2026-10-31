package gog.my_project.data_base.migration.builder.ast.drop_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.drop_index.IMigrationDropIndexApi
import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst
import gog.my_project.data_base.migration.ast.schema.drop_index.MigrationDropIndexAst

class MigrationDropIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationDropIndexAst = MigrationDropIndexAst(),
) : IMigrationDropIndexApi {
    override fun tableName(table: String): IMigrationDropIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): IMigrationDropIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    fun build(): IMigrationDropIndexApi = this
}
