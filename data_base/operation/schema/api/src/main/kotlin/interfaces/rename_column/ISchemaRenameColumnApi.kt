package gog.my_project.data_base_schema.api.interfaces.rename_column

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst

interface ISchemaRenameColumnApi : ISchemaApi<ISchemaRenameColumnAst> {
    fun tableName(table: String): ISchemaRenameColumnApi
    fun name(name: String): ISchemaRenameColumnApi
    fun to(name: String): ISchemaRenameColumnApi
}
