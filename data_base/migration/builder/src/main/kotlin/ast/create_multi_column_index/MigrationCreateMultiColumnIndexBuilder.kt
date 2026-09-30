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
        ast.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateMultiColumnIndexApi = apply {
        ast.indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateMultiColumnIndexApi = apply {
        // Each call replaces the previous list; retain the supplied order and original casing.
        ast.columnNames = columns.map(String::trim)
    }

    fun build(): IMigrationCreateMultiColumnIndexApi {
        require(!ast.tableName.isNullOrBlank()) { "CREATE INDEX requires a non-blank table name" }
        require(!ast.indexName.isNullOrBlank()) { "CREATE INDEX requires a non-blank index name" }
        val names = requireNotNull(ast.columnNames) { "CREATE INDEX requires at least two columns" }
        require(names.size >= 2) { "CREATE INDEX requires at least two columns" }
        require(names.all(String::isNotBlank)) { "CREATE INDEX column names must be non-blank" }
        requireNoDuplicateColumns(names)
        return this
    }

    private fun requireNoDuplicateColumns(names: List<String>) {
        for (currentIndex in names.indices) {
            for (previousIndex in 0 until currentIndex) {
                require(!names[currentIndex].trim().equals(names[previousIndex].trim(), ignoreCase = true)) {
                    "CREATE INDEX column names must not contain duplicates"
                }
            }
        }
    }
}
