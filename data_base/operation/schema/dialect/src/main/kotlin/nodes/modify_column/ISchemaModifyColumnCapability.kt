package gog.my_project.data_base_schema.dialect.nodes.modify_column

import gog.my_project.data_base_schema.ast.interfaces.modify_column.ISchemaModifyColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaModifyColumnCapability : IAstRenderer<ISchemaModifyColumnAst, SchemaDataClass?>
