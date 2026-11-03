package gog.my_project.data_base_schema.builder.ast.create_index

import gog.my_project.data_base_schema.api.interfaces.create_index.ISchemaCreateIndexApi
import gog.my_project.data_base_schema.ast.interfaces.create_index.ISchemaCreateIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base_schema.ast.schema.create_index.SchemaCreateIndexAst

class SchemaCreateIndexBuilder(
    override var ast: ISchemaCreateIndexAst = SchemaCreateIndexAst(),
) : ISchemaCreateIndexApi {
    override fun tableName(table: String): ISchemaCreateIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaCreateIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun column(column: String): ISchemaCreateIndexApi = apply {
        ast.metadata.columnName = column.trim()
    }

    override fun using(method: IndexMethod): ISchemaCreateIndexApi = apply {
        ast.metadata.indexMethod = method
    }

    fun build(): ISchemaCreateIndexApi = this
}
