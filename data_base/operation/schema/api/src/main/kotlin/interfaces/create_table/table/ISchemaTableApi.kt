package gog.my_project.data_base_schema.api.interfaces.create_table.table

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst

interface ISchemaTableApi: ISchemaApi<ISchemaTableAst> {

    fun tableName(table: String): ISchemaTableApi;

}