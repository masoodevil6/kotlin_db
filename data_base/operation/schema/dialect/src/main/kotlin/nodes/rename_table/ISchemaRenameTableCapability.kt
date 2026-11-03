package gog.my_project.data_base_schema.dialect.nodes.rename_table

import gog.my_project.data_base_schema.ast.interfaces.rename_table.ISchemaRenameTableAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaRenameTableCapability :
    IAstRenderer<ISchemaRenameTableAst, SchemaDataClass?>