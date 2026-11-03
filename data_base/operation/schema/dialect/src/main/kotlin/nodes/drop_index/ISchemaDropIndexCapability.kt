package gog.my_project.data_base_schema.dialect.nodes.drop_index

import gog.my_project.data_base_schema.ast.interfaces.drop_index.ISchemaDropIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IRenderContext
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaDropIndexCapability : IAstRenderer<ISchemaDropIndexAst, SchemaDataClass?>
