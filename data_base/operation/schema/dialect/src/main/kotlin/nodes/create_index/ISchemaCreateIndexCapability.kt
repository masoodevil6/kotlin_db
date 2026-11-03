package gog.my_project.data_base_schema.dialect.nodes.create_index

import gog.my_project.data_base_schema.ast.interfaces.create_index.ISchemaCreateIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaCreateIndexCapability : IAstRenderer<ISchemaCreateIndexAst, SchemaDataClass?>
