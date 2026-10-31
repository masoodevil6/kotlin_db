package gog.my_project.data_base.migration.builder.ast.create_unique_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_unique_index.IMigrationCreateUniqueIndexApi
import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst
import gog.my_project.data_base.migration.ast.schema.create_unique_index.MigrationCreateUniqueIndexAst

class MigrationCreateUniqueIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateUniqueIndexAst = MigrationCreateUniqueIndexAst(),
) : IMigrationCreateUniqueIndexApi {
    override fun tableName(table: String): IMigrationCreateUniqueIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateUniqueIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun column(column: String): IMigrationCreateUniqueIndexApi = apply {
        ast.metadata.columnName = column.trim()
    }

    fun build(): IMigrationCreateUniqueIndexApi = this
}
