package gog.my_project.data_base_schema.dialect.nodes.create_unique_index

import gog.my_project.data_base_schema.ast.interfaces.create_unique_index.ISchemaCreateUniqueIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaCreateUniqueIndexCapability :
    IAstRenderer<ISchemaCreateUniqueIndexAst, SchemaDataClass?>
