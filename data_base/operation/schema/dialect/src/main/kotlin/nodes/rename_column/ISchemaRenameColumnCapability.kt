package gog.my_project.data_base_schema.dialect.nodes.rename_column

import gog.my_project.data_base_schema.ast.interfaces.rename_column.ISchemaRenameColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaRenameColumnCapability : IAstRenderer<ISchemaRenameColumnAst, SchemaDataClass?>
