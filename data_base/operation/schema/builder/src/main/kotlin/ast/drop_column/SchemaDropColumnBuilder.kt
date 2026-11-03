package gog.my_project.data_base_schema.builder.ast.drop_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.drop_column.ISchemaDropColumnApi
import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst
import gog.my_project.data_base_schema.ast.schema.drop_column.SchemaDropColumnAst

class SchemaDropColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaDropColumnAst = SchemaDropColumnAst(),
) : ISchemaDropColumnApi {

    override fun tableName(table: String): ISchemaDropColumnApi {
        ast.metadata.tableName = table.trim()
        return this
    }

    override fun name(name: String): ISchemaDropColumnApi {
        ast.metadata.name = name.trim()
        return this
    }

    override fun ifExists(): ISchemaDropColumnApi {
        ast.metadata.ifExists = true
        return this
    }

    fun build(): ISchemaDropColumnApi = this
}
