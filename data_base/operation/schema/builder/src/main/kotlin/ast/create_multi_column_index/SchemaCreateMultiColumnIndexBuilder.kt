package gog.my_project.data_base_schema.builder.ast.create_multi_column_index

import gog.my_project.data_base_schema.api.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexApi
import gog.my_project.data_base_schema.ast.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexAst
import gog.my_project.data_base_schema.ast.schema.create_multi_column_index.SchemaCreateMultiColumnIndexAst

class SchemaCreateMultiColumnIndexBuilder(
    override var ast: ISchemaCreateMultiColumnIndexAst = SchemaCreateMultiColumnIndexAst(),
) : ISchemaCreateMultiColumnIndexApi {
    override fun tableName(table: String): ISchemaCreateMultiColumnIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaCreateMultiColumnIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun columns(vararg columns: String): ISchemaCreateMultiColumnIndexApi = apply {
        // Each call replaces the previous list; retain the supplied order and original casing.
        ast.metadata.columnNames = columns.map(String::trim)
    }

    fun build(): ISchemaCreateMultiColumnIndexApi = this
}
