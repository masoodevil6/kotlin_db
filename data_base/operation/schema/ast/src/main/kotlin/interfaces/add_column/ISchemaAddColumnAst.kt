package gog.my_project.data_base_schema.ast.interfaces.add_column

import gog.my_project.data_base_schema.ast.interfaces.ISchemaAst
import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.ast.metadata.add_column.SchemaAddColumnAstMetadata

interface ISchemaAddColumnAst : ISchemaAst<SchemaAddColumnAstMetadata> {
    var columnAst: ISchemaColumnAst?
}
