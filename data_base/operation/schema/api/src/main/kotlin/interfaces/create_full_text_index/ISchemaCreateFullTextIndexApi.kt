package gog.my_project.data_base_schema.api.interfaces.create_full_text_index

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_full_text_index.ISchemaCreateFullTextIndexAst

interface ISchemaCreateFullTextIndexApi : ISchemaApi<ISchemaCreateFullTextIndexAst> {
    fun tableName(table: String): ISchemaCreateFullTextIndexApi
    fun name(name: String): ISchemaCreateFullTextIndexApi
    fun columns(vararg columns: String): ISchemaCreateFullTextIndexApi
}
