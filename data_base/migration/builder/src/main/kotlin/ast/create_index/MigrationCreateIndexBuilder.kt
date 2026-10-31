package gog.my_project.data_base.migration.builder.ast.create_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_index.IMigrationCreateIndexApi
import gog.my_project.data_base.migration.ast.interfaces.create_index.IMigrationCreateIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.schema.create_index.MigrationCreateIndexAst

class MigrationCreateIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateIndexAst = MigrationCreateIndexAst(),
) : IMigrationCreateIndexApi {
    override fun tableName(table: String): IMigrationCreateIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun column(column: String): IMigrationCreateIndexApi = apply {
        ast.metadata.columnName = column.trim()
    }

    override fun using(method: IndexMethod): IMigrationCreateIndexApi = apply {
        ast.metadata.indexMethod = method
    }

    fun build(): IMigrationCreateIndexApi = this
}
