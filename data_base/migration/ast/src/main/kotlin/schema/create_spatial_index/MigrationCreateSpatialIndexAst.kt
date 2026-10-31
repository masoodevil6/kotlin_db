package gog.my_project.data_base.migration.ast.schema.create_spatial_index

import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.ast.metadata.create_spatial_index.MigrationCreateSpatialIndexAstMetadata

class MigrationCreateSpatialIndexAst(
    override var metadata: MigrationCreateSpatialIndexAstMetadata = MigrationCreateSpatialIndexAstMetadata()
) : IMigrationCreateSpatialIndexAst {

}
