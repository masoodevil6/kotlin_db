package gog.my_project.data_base_schema.dialect.nodes.create_spatial_index

import gog.my_project.data_base_schema.ast.interfaces.create_spatial_index.ISchemaCreateSpatialIndexAst
import gog.my_project.data_base_schema.dialect.data_class.SchemaDataClass
import gog.my_project.data_base_schema.dialect.interfaces.IAstRenderer

interface ISchemaCreateSpatialIndexCapability :
    IAstRenderer<ISchemaCreateSpatialIndexAst, SchemaDataClass?>
