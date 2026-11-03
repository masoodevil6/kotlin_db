package gog.my_project.data_base_schema.dialect.nodes.drop_column

import gog.my_project.data_base_schema.ast.interfaces.drop_column.ISchemaDropColumnAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaDropColumnCapability :
    IAstRenderer<ISchemaDropColumnAst, SchemaDataClass?>
