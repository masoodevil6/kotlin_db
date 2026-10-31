package gog.my_project.data_base.migration.ast.metadata.create_unique_index

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationCreateUniqueIndexAstMetadata : IMigrationAstMetadata {
    var tableName:    String? = null
    var indexName:    String? = null
    var columnName:   String? = null
}
