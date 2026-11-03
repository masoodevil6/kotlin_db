package gog.my_project.data_base_schema.dialect.nodes.add_column

import gog.my_project.data_base_schema.ast.interfaces.add_column.ISchemaAddColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaAddColumnCapability : IAstRenderer<ISchemaAddColumnAst, SchemaDataClass?>