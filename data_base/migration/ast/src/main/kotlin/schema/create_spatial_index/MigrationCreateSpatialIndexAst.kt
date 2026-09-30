package gog.my_project.data_base.migration.ast.schema.create_spatial_index

import gog.my_project.data_base.migration.ast.interfaces.create_spatial_index.IMigrationCreateSpatialIndexAst

class MigrationCreateSpatialIndexAst : IMigrationCreateSpatialIndexAst {
    override var tableName: String? = null
    override var indexName: String? = null
    override var columnName: String? = null
}
