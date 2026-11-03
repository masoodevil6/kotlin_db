package gog.my_project.data_base_schema.dialect.nodes.create_table.table

import gog.my_project.data_base_schema.ast.interfaces.create_table.table.ISchemaTableAst
import gog.my_project.data_base_schema.dialect.data_class.create_table.table.SchemaTableData
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaTableCapability : IAstRenderer<ISchemaTableAst, SchemaTableData> {
}