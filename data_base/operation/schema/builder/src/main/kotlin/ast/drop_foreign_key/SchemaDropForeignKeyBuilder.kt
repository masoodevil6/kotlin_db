package gog.my_project.data_base_schema.builder.ast.drop_foreign_key

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.drop_foreign_key.ISchemaDropForeignKeyApi
import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.ast.schema.drop_foreign_key.SchemaDropForeignKeyAst

class SchemaDropForeignKeyBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaDropForeignKeyAst = SchemaDropForeignKeyAst(),
) : ISchemaDropForeignKeyApi {
    override fun tableName(table: String): ISchemaDropForeignKeyApi = apply {
        ast.metadata.tableName = table.trim()
    }

    override fun name(name: String): ISchemaDropForeignKeyApi = apply {
        ast.metadata.constraintName = name.trim()
    }

    fun build(): ISchemaDropForeignKeyApi = this
}
