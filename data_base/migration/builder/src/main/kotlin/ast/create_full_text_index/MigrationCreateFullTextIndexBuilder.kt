package gog.my_project.data_base.migration.builder.ast.create_full_text_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_full_text_index.IMigrationCreateFullTextIndexApi
import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.ast.schema.create_full_text_index.MigrationCreateFullTextIndexAst

class MigrationCreateFullTextIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateFullTextIndexAst = MigrationCreateFullTextIndexAst(),
) : IMigrationCreateFullTextIndexApi {
    override fun tableName(table: String): IMigrationCreateFullTextIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateFullTextIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateFullTextIndexApi = apply {
        // Each call replaces the previous list; retain supplied order and spelling.
        ast.metadata.columnNames = columns.map(String::trim)
    }

    fun build(): IMigrationCreateFullTextIndexApi = this
}
