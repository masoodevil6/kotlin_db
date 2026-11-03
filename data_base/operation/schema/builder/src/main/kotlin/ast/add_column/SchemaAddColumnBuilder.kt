package gog.my_project.data_base_schema.builder.ast.add_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.add_column.ISchemaAddColumnApi
import gog.my_project.data_base_schema.ast.interfaces.add_column.ISchemaAddColumnAst
import gog.my_project.data_base_schema.ast.schema.add_column.SchemaAddColumnAst
import gog.my_project.data_base_schema.ast.schema.create_table.column.SchemaColumnAst
import gog.my_project.data_base_schema.builder.ast.create_table.column.SchemaColumnBuilder
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType
class SchemaAddColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaAddColumnAst = SchemaAddColumnAst(),
) : ISchemaAddColumnApi {
    private val column = SchemaColumnAst()
    private val columnBuilder = SchemaColumnBuilder(params, column)

    init {
        ast.columnAst = column
    }

    override fun tableName(table: String): ISchemaAddColumnApi {
        ast.metadata.tableName = table.trim()
        return this
    }

    override fun name(name: String): ISchemaAddColumnApi {
        columnBuilder.name(name)
        return this
    }

    override fun dataType(dataType: SchemaColumnDataType): ISchemaAddColumnApi {
        columnBuilder.dataType(dataType)
        return this
    }

    override fun default(defaultValue: Any?): ISchemaAddColumnApi {
        columnBuilder.default(defaultValue)
        return this
    }

    override fun isNullable(status: Boolean): ISchemaAddColumnApi {
        columnBuilder.isNullable(status)
        return this
    }

    override fun isAutoIncrement(status: Boolean): ISchemaAddColumnApi {
        columnBuilder.isAutoIncrement(status)
        return this
    }

    override fun isPrimary(status: Boolean): ISchemaAddColumnApi {
        columnBuilder.isPrimary(status)
        return this
    }
}
