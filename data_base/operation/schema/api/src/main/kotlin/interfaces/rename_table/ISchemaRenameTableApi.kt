package gog.my_project.data_base_schema.api.interfaces.rename_table

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.rename_table.ISchemaRenameTableAst

interface ISchemaRenameTableApi : ISchemaApi<ISchemaRenameTableAst> {

    fun fromTableName(table: String): ISchemaRenameTableApi

    fun toTableName(table: String): ISchemaRenameTableApi
}