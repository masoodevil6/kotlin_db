package gog.my_project.data_base.migration.builder.ast.create_table.column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_table.table_column.IMigrationColumnApi
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.schema.create_table.column.MigrationColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: IMigrationColumnAst = MigrationColumnAst(),
) : IMigrationColumnApi {

    override fun name(name: String): IMigrationColumnApi {
        this.ast.columnName = name.trim();
        return this;
    }

    override fun dataType(dataType: MigrationColumnDataType): IMigrationColumnApi {
        this.ast.columnDataType = dataType;
        return this;
    }

    override fun default(defaultValue: Any?): IMigrationColumnApi {
        this.ast.columnDefault = defaultValue;
        this.ast.hasColumnDefault = true;
        return this;
    }

    override fun isNullable(status: Boolean): IMigrationColumnApi {
        this.ast.columnNullable = status;
        return this;
    }

    override fun isAutoIncrement(status: Boolean): IMigrationColumnApi {
        this.ast.columnAutoIncrement = status;
        return this;
    }

    override fun isPrimary(status: Boolean): IMigrationColumnApi {
        this.ast.columnPrimary = status;
        return this;
    }

}
