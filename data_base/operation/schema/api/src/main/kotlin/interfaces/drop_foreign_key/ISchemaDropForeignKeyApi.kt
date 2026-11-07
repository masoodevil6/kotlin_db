package gog.my_project.data_base_schema.api.interfaces.drop_foreign_key

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst

interface ISchemaDropForeignKeyApi : ISchemaApi<ISchemaDropForeignKeyAst> {
    fun tableName(table: String): ISchemaDropForeignKeyApi
    fun name(name: String): ISchemaDropForeignKeyApi
    fun ifExists(): ISchemaDropForeignKeyApi
}
