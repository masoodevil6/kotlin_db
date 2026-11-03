package gog.my_project.data_base_schema.api.interfaces.add_column

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.add_column.ISchemaAddColumnAst
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

interface ISchemaAddColumnApi : ISchemaApi<ISchemaAddColumnAst> {
    fun tableName(table: String): ISchemaAddColumnApi

    fun name(name: String): ISchemaAddColumnApi

    fun dataType(dataType: SchemaColumnDataType): ISchemaAddColumnApi

    fun default(defaultValue: Any?): ISchemaAddColumnApi

    fun isNullable(status: Boolean): ISchemaAddColumnApi

    fun isAutoIncrement(status: Boolean): ISchemaAddColumnApi

    fun isPrimary(status: Boolean): ISchemaAddColumnApi

    fun nullable(): ISchemaAddColumnApi = isNullable(true)

    fun notNull(): ISchemaAddColumnApi = isNullable(false)

    fun autoIncrement(): ISchemaAddColumnApi = isAutoIncrement(true)

    fun primaryKey(): ISchemaAddColumnApi = isPrimary(true)
}