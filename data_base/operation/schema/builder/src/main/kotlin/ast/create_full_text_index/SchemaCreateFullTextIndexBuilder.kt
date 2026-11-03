package gog.my_project.data_base_schema.builder.ast.create_full_text_index

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.create_full_text_index.ISchemaCreateFullTextIndexApi
import gog.my_project.data_base_schema.ast.interfaces.create_full_text_index.ISchemaCreateFullTextIndexAst
import gog.my_project.data_base_schema.ast.schema.create_full_text_index.SchemaCreateFullTextIndexAst

class SchemaCreateFullTextIndexBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaCreateFullTextIndexAst = SchemaCreateFullTextIndexAst(),
) : ISchemaCreateFullTextIndexApi {
    override fun tableName(table: String): ISchemaCreateFullTextIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaCreateFullTextIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateFullTextIndexApi = apply {
        // Each call replaces the previous list; retain supplied order and spelling.
        ast.metadata.columnNames = columns.map(String::trim)
    }

    fun build(): ISchemaCreateFullTextIndexApi = this
}
