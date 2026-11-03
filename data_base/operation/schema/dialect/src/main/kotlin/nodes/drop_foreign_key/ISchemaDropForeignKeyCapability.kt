package gog.my_project.data_base_schema.dialect.nodes.drop_foreign_key

import gog.my_project.data_base_schema.ast.interfaces.drop_foreign_key.ISchemaDropForeignKeyAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaDropForeignKeyCapability :
    IAstRenderer<ISchemaDropForeignKeyAst, SchemaDataClass?>
