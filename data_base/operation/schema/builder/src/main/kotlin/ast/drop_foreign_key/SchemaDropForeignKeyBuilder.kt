package gog.my_project.data_base_schema.builder.ast.drop_foreign_key

import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.ast.schema.drop_foreign_key.SchemaDropForeignKeyAst
import gog.my_project.data_base_schema.builder.ast.if_exist.SchemaIfExistBuilder

class SchemaDropForeignKeyBuilder(
    override var ast: ISchemaDropForeignKeyAst = SchemaDropForeignKeyAst(),
) : ISchemaDropForeignKeyApi {
    override fun tableName(table: String): ISchemaDropForeignKeyApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaDropForeignKeyApi = apply {
        ast.metadata.constraintName = name.trim()
    }

    override fun ifExists(): ISchemaDropForeignKeyApi = apply {
        ast.ifExistAst = SchemaIfExistBuilder().build().ast
    }

    fun build(): ISchemaDropForeignKeyApi = this
}
