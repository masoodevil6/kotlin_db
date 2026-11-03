package gog.my_project.data_base_schema.ast.schema.create_spatial_index

import gog.my_project.data_base_schema.ast.interfaces.create_spatial_index.ISchemaCreateSpatialIndexAst
import gog.my_project.data_base_schema.ast.metadata.create_spatial_index.SchemaCreateSpatialIndexAstMetadata

class SchemaCreateSpatialIndexAst(
    override var metadata: SchemaCreateSpatialIndexAstMetadata = SchemaCreateSpatialIndexAstMetadata()
) : ISchemaCreateSpatialIndexAst {

}
