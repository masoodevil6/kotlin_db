package gog.my_project.data_base.migration.builder.ast.modify_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.modify_column.IMigrationModifyColumnApi
import gog.my_project.data_base.migration.ast.interfaces.modify_column.ColumnNullability
import gog.my_project.data_base.migration.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.ast.schema.modify_column.MigrationModifyColumnAst
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationModifyColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
) : IMigrationModifyColumnApi {
    private var table: String? = null
    private var column: String? = null
    private var type: MigrationColumnDataType? = null
    private var nullable: ColumnNullability? = null
    private var defaultDefinition: DefaultDefinition = DefaultDefinition.NoDefaultClause
    private var autoIncrementState: Boolean? = null

    override var ast: IMigrationModifyColumnAst
        get() = MigrationModifyColumnAst(
            tableName = requireValue(table, "table name"),
            columnName = requireValue(column, "column name"),
            columnDataType = requireNotNull(type) { "MODIFY COLUMN requires a data type" },
            nullability = requireNotNull(nullable) { "MODIFY COLUMN requires explicit nullability" },
            defaultDefinition = defaultDefinition,
            autoIncrement = requireNotNull(autoIncrementState) {
                "MODIFY COLUMN requires an explicit AUTO_INCREMENT state"
            },
        )
        set(value) { throw UnsupportedOperationException("MODIFY COLUMN AST is built from DSL state") }

    override fun tableName(table: String): IMigrationModifyColumnApi = apply { this.table = table.trim() }
    override fun name(name: String): IMigrationModifyColumnApi = apply { column = name.trim() }
    override fun dataType(dataType: MigrationColumnDataType): IMigrationModifyColumnApi = apply { type = dataType }
    override fun default(defaultValue: Any?): IMigrationModifyColumnApi = apply {
        defaultDefinition = if (defaultValue == null) DefaultDefinition.DefaultNull
        else DefaultDefinition.DefaultValue(defaultValue)
    }
    override fun isNullable(status: Boolean): IMigrationModifyColumnApi = apply {
        nullable = if (status) ColumnNullability.NULL else ColumnNullability.NOT_NULL
    }
    override fun autoIncrement(status: Boolean): IMigrationModifyColumnApi = apply { autoIncrementState = status }

    private fun requireValue(value: String?, label: String): String =
        requireNotNull(value?.takeIf(String::isNotBlank)) { "MODIFY COLUMN requires a non-blank $label" }
}
