package gog.my_project.data_base_schema.dialect.nodes.create_full_text_index

import gog.my_project.data_base_schema.ast.interfaces.create_full_text_index.ISchemaCreateFullTextIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaCreateFullTextIndexCapability :
    IAstRenderer<ISchemaCreateFullTextIndexAst, SchemaDataClass?>
