package gog.my_project.data_base_schema.builder.ast.create_table.table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.create_table.table.ISchemaTableApi
import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.ast.schema.create_table.table.SchemaTableAst

class SchemaTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf<SqlParameter<*>>(),
    override var ast: ISchemaTableAst = SchemaTableAst()
) : ISchemaTableApi {

    override fun tableName(table: String): ISchemaTableApi {
        this.ast.metadata.tableName = table.trim();
        return this;
    }

}
