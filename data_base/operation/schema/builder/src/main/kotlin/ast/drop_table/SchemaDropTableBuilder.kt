package gog.my_project.data_base_schema.builder.ast.drop_table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.drop_table.ISchemaDropTableApi
import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst
import gog.my_project.data_base_schema.ast.schema.drop_table.SchemaDropTableAst

class SchemaDropTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaDropTableAst = SchemaDropTableAst(),
) : ISchemaDropTableApi {

    override fun tableName(table: String): ISchemaDropTableApi {
        ast.metadata.tableName = table.trim()
        return this
    }

    override fun ifExists(): ISchemaDropTableApi {
        ast.metadata.ifExists = true
        return this
    }
}
