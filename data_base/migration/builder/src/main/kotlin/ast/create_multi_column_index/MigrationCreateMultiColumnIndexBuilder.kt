package gog.my_project.data_base.migration.builder.ast.create_multi_column_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexApi
import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst
import gog.my_project.data_base.migration.ast.schema.create_multi_column_index.MigrationCreateMultiColumnIndexAst

class MigrationCreateMultiColumnIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateMultiColumnIndexAst = MigrationCreateMultiColumnIndexAst(),
) : IMigrationCreateMultiColumnIndexApi {
    override fun tableName(table: String): IMigrationCreateMultiColumnIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateMultiColumnIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateMultiColumnIndexApi = apply {
        // Each call replaces the previous list; retain the supplied order and original casing.
        ast.metadata.columnNames = columns.map(String::trim)
    }

    fun build(): IMigrationCreateMultiColumnIndexApi = this
}
