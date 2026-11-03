package gog.my_project.data_base_schema.builder.ast.rename_column

import gog.my_project.data_base_schema.api.interfaces.rename_column.ISchemaRenameColumnApi
import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst
import gog.my_project.data_base_schema.ast.schema.rename_column.SchemaRenameColumnAst

class SchemaRenameColumnBuilder(
    override var ast: ISchemaRenameColumnAst = SchemaRenameColumnAst(),
) : ISchemaRenameColumnApi {
    override fun tableName(table: String): ISchemaRenameColumnApi {
        ast.metadata.tableName = table.trim()
        return this
    }
    override fun name(name: String): ISchemaRenameColumnApi {
        ast.metadata.name = name.trim()
        return this
    }
    override fun to(name: String): ISchemaRenameColumnApi {
        ast.metadata.to = name.trim()
        return this
    }
}
