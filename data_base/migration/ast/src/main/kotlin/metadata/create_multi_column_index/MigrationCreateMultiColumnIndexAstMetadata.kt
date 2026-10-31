package gog.my_project.data_base.migration.ast.metadata.create_multi_column_index

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationCreateMultiColumnIndexAstMetadata : IMigrationAstMetadata {
    var tableName:     String? = null
    var indexName:     String? = null
    var columnNames:   List<String>? = null
}
