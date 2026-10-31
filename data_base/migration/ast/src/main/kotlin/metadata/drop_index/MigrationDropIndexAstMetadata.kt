package gog.my_project.data_base.migration.ast.metadata.drop_index

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationDropIndexAstMetadata : IMigrationAstMetadata {
    var tableName: String? = null
    var indexName: String? = null
}
