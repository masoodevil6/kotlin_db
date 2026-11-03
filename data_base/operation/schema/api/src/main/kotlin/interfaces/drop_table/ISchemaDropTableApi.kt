package gog.my_project.data_base_schema.api.interfaces.drop_table

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst

interface ISchemaDropTableApi : ISchemaApi<ISchemaDropTableAst> {

    fun tableName(table: String): ISchemaDropTableApi

    fun ifExists(): ISchemaDropTableApi
}