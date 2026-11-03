package gog.my_project.data_base_schema.dialect.nodes.drop_table

import gog.my_project.data_base_schema.ast.interfaces.drop_table.ISchemaDropTableAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaDropTableCapability :
    IAstRenderer<ISchemaDropTableAst, SchemaDataClass?>