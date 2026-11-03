package gog.my_project.data_base_schema.api.interfaces.create_multi_column_index

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_multi_column_index.ISchemaCreateMultiColumnIndexAst

interface ISchemaCreateMultiColumnIndexApi : ISchemaApi<ISchemaCreateMultiColumnIndexAst> {
    fun tableName(table: String): ISchemaCreateMultiColumnIndexApi
    fun name(name: String): ISchemaCreateMultiColumnIndexApi
    fun columns(vararg columns: String): ISchemaCreateMultiColumnIndexApi
}
