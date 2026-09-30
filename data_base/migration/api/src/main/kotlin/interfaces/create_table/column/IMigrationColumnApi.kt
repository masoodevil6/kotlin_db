package gog.my_project.data_base.migration.api.interfaces.create_table.table_column

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

interface IMigrationColumnApi: IMigrationApi<IMigrationColumnAst>  {

    fun name(name: String): IMigrationColumnApi;

    fun dataType(dataType: MigrationColumnDataType): IMigrationColumnApi;

    fun default(defaultValue: Any?): IMigrationColumnApi;

    fun isNullable(status: Boolean): IMigrationColumnApi;

    fun isAutoIncrement(status: Boolean): IMigrationColumnApi;

    fun isPrimary(status: Boolean): IMigrationColumnApi;

    fun nullable(): IMigrationColumnApi = isNullable(true)

    fun notNull(): IMigrationColumnApi = isNullable(false)

    fun autoIncrement(): IMigrationColumnApi = isAutoIncrement(true)

    fun primaryKey(): IMigrationColumnApi = isPrimary(true)

}
