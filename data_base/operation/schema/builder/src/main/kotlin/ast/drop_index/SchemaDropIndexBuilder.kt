package gog.my_project.data_base_schema.builder.ast.drop_index

import gog.my_project.data_base_schema.api.interfaces.drop_index.ISchemaDropIndexApi
import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst
import gog.my_project.data_base_schema.ast.schema.drop_index.SchemaDropIndexAst
import gog.my_project.data_base_schema.builder.ast.if_exist.SchemaIfExistBuilder

class SchemaDropIndexBuilder(
    override var ast: ISchemaDropIndexAst = SchemaDropIndexAst(),
) : ISchemaDropIndexApi {
    override fun tableName(table: String): ISchemaDropIndexApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaDropIndexApi = apply {
        ast.metadata.indexName = name.trim()
    }

    override fun ifExists(): ISchemaDropIndexApi = apply {
        ast.ifExistAst = SchemaIfExistBuilder().build().ast
    }

    fun build(): ISchemaDropIndexApi = this
}
