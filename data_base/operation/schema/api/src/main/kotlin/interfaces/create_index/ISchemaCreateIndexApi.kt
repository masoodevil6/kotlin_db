package gog.my_project.data_base_schema.api.interfaces.create_index

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_index.ISchemaCreateIndexAst
import gog.my_project.data_base_schema.ast.interfaces.create_index.IndexMethod

interface ISchemaCreateIndexApi : ISchemaApi<ISchemaCreateIndexAst> {
    fun tableName(table: String): ISchemaCreateIndexApi
    fun name(name: String): ISchemaCreateIndexApi
    fun column(column: String): ISchemaCreateIndexApi
    fun using(method: IndexMethod): ISchemaCreateIndexApi
}
