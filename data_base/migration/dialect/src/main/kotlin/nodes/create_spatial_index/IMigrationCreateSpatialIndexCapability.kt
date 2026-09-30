package gog.my_project.data_base.migration.dialect.nodes.create_spatial_index

import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst
import gog.my_project.data_base.migration.dialect.data_class.MigrationDataClass
import gog.my_project.data_base.migration.dialect.interfaces.IAstRenderer

interface IMigrationCreateSpatialIndexCapability :
    IAstRenderer<IMigrationCreateSpatialIndexAst, MigrationDataClass?>
