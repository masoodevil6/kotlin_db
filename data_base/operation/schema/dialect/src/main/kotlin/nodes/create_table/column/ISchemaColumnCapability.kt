package gog.my_project.data_base_schema.dialect.nodes.create_table.column

import gog.my_project.data_base_schema.ast.interfaces.create_table.column.ISchemaColumnAst
import gog.my_project.data_base_schema.dialect.data_class.create_table.column.SchemaColumnData
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaColumnCapability : IAstRenderer<ISchemaColumnAst, SchemaColumnData> {
}