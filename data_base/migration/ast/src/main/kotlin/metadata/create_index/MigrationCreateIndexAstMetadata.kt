package gog.my_project.data_base.migration.ast.metadata.create_index

import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationCreateIndexAstMetadata : IMigrationAstMetadata {
    var tableName:      String? = null
    var indexName:      String? = null
    var columnName:     String? = null
    var indexMethod:    IndexMethod? = null
}
