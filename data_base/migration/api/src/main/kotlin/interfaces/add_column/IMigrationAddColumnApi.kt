package gog.my_project.data_base.migration.api.interfaces.add_column

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

interface IMigrationAddColumnApi : IMigrationApi<IMigrationAddColumnAst> {
    fun tableName(table: String): IMigrationAddColumnApi

    fun name(name: String): IMigrationAddColumnApi

    fun dataType(dataType: MigrationColumnDataType): IMigrationAddColumnApi

    fun default(defaultValue: Any?): IMigrationAddColumnApi

    fun isNullable(status: Boolean): IMigrationAddColumnApi

    fun isAutoIncrement(status: Boolean): IMigrationAddColumnApi

    fun isPrimary(status: Boolean): IMigrationAddColumnApi

    fun nullable(): IMigrationAddColumnApi = isNullable(true)

    fun notNull(): IMigrationAddColumnApi = isNullable(false)

    fun autoIncrement(): IMigrationAddColumnApi = isAutoIncrement(true)

    fun primaryKey(): IMigrationAddColumnApi = isPrimary(true)
}