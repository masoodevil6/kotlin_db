package gog.my_project.data_base.migration.ast.interfaces.create_spatial_index

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationCreateSpatialIndexAst : IMigrationAst {
    var tableName: String?
    var indexName: String?
    var columnName: String?
}
