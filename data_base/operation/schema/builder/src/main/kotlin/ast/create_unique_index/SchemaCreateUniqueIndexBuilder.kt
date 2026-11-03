package gog.my_project.data_base_schema.builder.ast.create_unique_index

import gog.my_project.data_base_schema.api.interfaces.create_unique_index.ISchemaCreateUniqueIndexApi
import gog.my_project.data_base_schema.ast.interfaces.create_unique_index.ISchemaCreateUniqueIndexAst
import gog.my_project.data_base_schema.ast.schema.create_unique_index.SchemaCreateUniqueIndexAst

class SchemaCreateUniqueIndexBuilder(
    override var ast: ISchemaCreateUniqueIndexAst = SchemaCreateUniqueIndexAst(),
) : ISchemaCreateUniqueIndexApi {
    override fun tableName(table: String): ISchemaCreateUniqueIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaCreateUniqueIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun column(column: String): ISchemaCreateUniqueIndexApi = apply {
        ast.metadata.columnName = column.trim()
    }

    fun build(): ISchemaCreateUniqueIndexApi = this
}
