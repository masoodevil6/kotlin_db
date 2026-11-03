package gog.my_project.data_base_schema.api.interfaces.drop_column

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst

interface ISchemaDropColumnApi : ISchemaApi<ISchemaDropColumnAst> {
    fun tableName(table: String): ISchemaDropColumnApi

    fun name(name: String): ISchemaDropColumnApi

    fun ifExists(): ISchemaDropColumnApi
}
