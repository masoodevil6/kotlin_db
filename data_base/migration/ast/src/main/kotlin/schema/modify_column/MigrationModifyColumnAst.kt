package gog.my_project.data_base.migration.ast.schema.modify_column

import gog.my_project.data_base.migration.ast.interfaces.modify_column.ColumnNullability
import gog.my_project.data_base.migration.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

data class MigrationModifyColumnAst(
    override val tableName: String,
    override val columnName: String,
    override val columnDataType: MigrationColumnDataType,
    override val nullability: ColumnNullability,
    override val defaultDefinition: DefaultDefinition,
    override val autoIncrement: Boolean,
) : IMigrationModifyColumnAst
