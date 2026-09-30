package gog.my_project.data_base.migration.builder.ast.add_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.add_column.IMigrationAddColumnApi
import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.ast.schema.add_column.MigrationAddColumnAst
import gog.my_project.data_base.migration.ast.schema.create_table.column.MigrationColumnAst
import gog.my_project.data_base.migration.builder.ast.create_table.column.MigrationColumnBuilder
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationAddColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationAddColumnAst = MigrationAddColumnAst(),
) : IMigrationAddColumnApi {
    private val column = MigrationColumnAst()
    private val columnBuilder = MigrationColumnBuilder(params, column)

    init {
        ast.columnAst = column
    }

    override fun tableName(table: String): IMigrationAddColumnApi {
        ast.tableName = table.trim()
        return this
    }

    override fun name(name: String): IMigrationAddColumnApi {
        columnBuilder.name(name)
        return this
    }

    override fun dataType(dataType: MigrationColumnDataType): IMigrationAddColumnApi {
        columnBuilder.dataType(dataType)
        return this
    }

    override fun default(defaultValue: Any?): IMigrationAddColumnApi {
        columnBuilder.default(defaultValue)
        return this
    }

    override fun isNullable(status: Boolean): IMigrationAddColumnApi {
        columnBuilder.isNullable(status)
        return this
    }

    override fun isAutoIncrement(status: Boolean): IMigrationAddColumnApi {
        columnBuilder.isAutoIncrement(status)
        return this
    }

    override fun isPrimary(status: Boolean): IMigrationAddColumnApi {
        columnBuilder.isPrimary(status)
        return this
    }
}