package gog.my_project.data_base_schema.api.interfaces.modify_column

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

interface ISchemaModifyColumnApi : ISchemaApi<ISchemaModifyColumnAst> {
    fun tableName(table: String): ISchemaModifyColumnApi
    fun name(name: String): ISchemaModifyColumnApi
    fun dataType(dataType: SchemaColumnDataType): ISchemaModifyColumnApi
    fun default(defaultValue: Any?): ISchemaModifyColumnApi
    fun isNullable(status: Boolean): ISchemaModifyColumnApi
    fun autoIncrement(status: Boolean): ISchemaModifyColumnApi

    fun nullable(): ISchemaModifyColumnApi = isNullable(true)
    fun notNull(): ISchemaModifyColumnApi = isNullable(false)
}
