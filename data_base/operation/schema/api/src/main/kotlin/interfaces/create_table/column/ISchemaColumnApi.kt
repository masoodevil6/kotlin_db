package gog.my_project.data_base_schema.api.interfaces.create_table.table_column

import gog.my_project.data_base_schema.api.interfaces.ISchemaApi
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

interface ISchemaColumnApi: ISchemaApi<ISchemaColumnAst>  {

    fun name(name: String): ISchemaColumnApi;

    fun dataType(dataType: SchemaColumnDataType): ISchemaColumnApi;

    fun default(defaultValue: Any?): ISchemaColumnApi;

    fun isNullable(status: Boolean): ISchemaColumnApi;

    fun isAutoIncrement(status: Boolean): ISchemaColumnApi;

    fun isPrimary(status: Boolean): ISchemaColumnApi;

    fun nullable(): ISchemaColumnApi = isNullable(true)

    fun notNull(): ISchemaColumnApi = isNullable(false)

    fun autoIncrement(): ISchemaColumnApi = isAutoIncrement(true)

    fun primaryKey(): ISchemaColumnApi = isPrimary(true)

}
