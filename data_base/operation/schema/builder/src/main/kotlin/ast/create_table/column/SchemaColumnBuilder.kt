package gog.my_project.data_base_schema.builder.ast.create_table.column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.create_table.table_column.ISchemaColumnApi
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.schema.create_table.column.SchemaColumnAst
import gog.my_project.data_base_schema.params.data_types.SchemaColumnDataType

class SchemaColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: ISchemaColumnAst = SchemaColumnAst(),
) : ISchemaColumnApi {

    override fun name(name: String): ISchemaColumnApi {
        this.ast.metadata.columnName = name.trim();
        return this;
    }

    override fun dataType(dataType: SchemaColumnDataType): ISchemaColumnApi {
        this.ast.metadata.columnDataType = dataType;
        return this;
    }

    override fun default(defaultValue: Any?): ISchemaColumnApi {
        this.ast.metadata.columnDefault = defaultValue;
        this.ast.metadata.hasColumnDefault = true;
        return this;
    }

    override fun isNullable(status: Boolean): ISchemaColumnApi {
        this.ast.metadata.columnNullable = status;
        return this;
    }

    override fun isAutoIncrement(status: Boolean): ISchemaColumnApi {
        this.ast.metadata.columnAutoIncrement = status;
        return this;
    }

    override fun isPrimary(status: Boolean): ISchemaColumnApi {
        this.ast.metadata.columnPrimary = status;
        return this;
    }

}
