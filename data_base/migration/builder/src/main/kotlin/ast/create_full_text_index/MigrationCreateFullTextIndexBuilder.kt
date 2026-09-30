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
        ast.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateFullTextIndexApi = apply {
        ast.indexName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateFullTextIndexApi = apply {
        // Each call replaces the previous list; retain supplied order and spelling.
        ast.columnNames = columns.map(String::trim)
    }

    fun build(): IMigrationCreateFullTextIndexApi {
        require(!ast.tableName.isNullOrBlank()) { "CREATE FULLTEXT INDEX requires a non-blank table name" }
        require(!ast.indexName.isNullOrBlank()) { "CREATE FULLTEXT INDEX requires a non-blank index name" }
        val names = requireNotNull(ast.columnNames) { "CREATE FULLTEXT INDEX requires at least one column" }
        require(names.isNotEmpty()) { "CREATE FULLTEXT INDEX requires at least one column" }
        require(names.all(String::isNotBlank)) { "CREATE FULLTEXT INDEX column names must be non-blank" }
        requireNoDuplicateColumns(names)
        return this
    }

    private fun requireNoDuplicateColumns(names: List<String>) {
        for (currentIndex in names.indices) {
            for (previousIndex in 0 until currentIndex) {
                require(!names[currentIndex].trim().equals(names[previousIndex].trim(), ignoreCase = true)) {
                    "CREATE FULLTEXT INDEX column names must not contain duplicates"
                }
            }
        }
    }
}
