package gog.my_project.data_base_schema.api.interfaces.create_unique_index

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_unique_index.ISchemaCreateUniqueIndexAst

interface ISchemaCreateUniqueIndexApi : ISchemaApi<ISchemaCreateUniqueIndexAst> {
    fun tableName(table: String): ISchemaCreateUniqueIndexApi
    fun name(name: String): ISchemaCreateUniqueIndexApi
    fun column(column: String): ISchemaCreateUniqueIndexApi
}
