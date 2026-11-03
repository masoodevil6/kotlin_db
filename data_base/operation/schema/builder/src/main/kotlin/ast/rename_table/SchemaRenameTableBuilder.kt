package gog.my_project.data_base_schema.builder.ast.rename_table

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base_schema.api.interfaces.rename_table.ISchemaRenameTableApi
import gog.my_project.data_base_schema.ast.interfaces.rename_table.ISchemaRenameTableAst
import gog.my_project.data_base_schema.ast.schema.rename_table.SchemaRenameTableAst

class SchemaRenameTableBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: ISchemaRenameTableAst = SchemaRenameTableAst(),
) : ISchemaRenameTableApi {

    override fun fromTableName(table: String): ISchemaRenameTableApi {
        ast.metadata.fromTableName = table.trim()
        return this
    }

    override fun toTableName(table: String): ISchemaRenameTableApi {
        ast.metadata.toTableName = table.trim()
        return this
    }
}
