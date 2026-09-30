package gog.my_project.data_base.migration.api.interfaces.modify_column

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

interface IMigrationModifyColumnApi : IMigrationApi<IMigrationModifyColumnAst> {
    fun tableName(table: String): IMigrationModifyColumnApi
    fun name(name: String): IMigrationModifyColumnApi
    fun dataType(dataType: MigrationColumnDataType): IMigrationModifyColumnApi
    fun default(defaultValue: Any?): IMigrationModifyColumnApi
    fun isNullable(status: Boolean): IMigrationModifyColumnApi
    fun autoIncrement(status: Boolean): IMigrationModifyColumnApi

    fun nullable(): IMigrationModifyColumnApi = isNullable(true)
    fun notNull(): IMigrationModifyColumnApi = isNullable(false)
}
