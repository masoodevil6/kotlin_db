package gog.my_project.data_base_schema.builder.ast.modify_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.modify_column.ISchemaModifyColumnApi
import gog.my_project.data_base_schema.ast.interfaces.modify_column.ColumnNullability
import gog.my_project.data_base_schema.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst
import gog.my_project.data_base_schema.ast.schema.modify_column.SchemaModifyColumnAst
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

class SchemaModifyColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
) : ISchemaModifyColumnApi {
    private var table: String? = null
    private var column: String? = null
    private var type: SchemaColumnDataType? = null
    private var nullable: ColumnNullability? = null
    private var defaultDefinition: DefaultDefinition = DefaultDefinition.NoDefaultClause
    private var autoIncrementState: Boolean? = null

    override var ast: ISchemaModifyColumnAst
        get() = SchemaModifyColumnAst().apply {
            metadata.tableName = table
            metadata.columnName = column
            metadata.columnDataType = type
            metadata.nullability = nullable
            metadata.defaultDefinition = this@SchemaModifyColumnBuilder.defaultDefinition
            metadata.autoIncrement = autoIncrementState ?: false
        }
        set(value) { throw UnsupportedOperationException("MODIFY COLUMN AST is built from DSL state") }

    override fun tableName(table: String): ISchemaModifyColumnApi = apply { this.table = table.trim() }
    override fun name(name: String): ISchemaModifyColumnApi = apply { column = name.trim() }
    override fun dataType(dataType: SchemaColumnDataType): ISchemaModifyColumnApi = apply { type = dataType }
    override fun default(defaultValue: Any?): ISchemaModifyColumnApi = apply {
        defaultDefinition = if (defaultValue == null) DefaultDefinition.DefaultNull
        else DefaultDefinition.DefaultValue(defaultValue)
    }
    override fun isNullable(status: Boolean): ISchemaModifyColumnApi = apply {
        nullable = if (status) ColumnNullability.NULL else ColumnNullability.NOT_NULL
    }
    override fun autoIncrement(status: Boolean): ISchemaModifyColumnApi = apply { autoIncrementState = status }

}
