package gog.my_project.data_base.migration.ast.metadata.create_full_text_index

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationCreateFullTextIndexAstMetadata : IMigrationAstMetadata {
    var tableName:     String? = null
    var indexName:     String? = null
    var columnNames:   List<String>? = null
}
