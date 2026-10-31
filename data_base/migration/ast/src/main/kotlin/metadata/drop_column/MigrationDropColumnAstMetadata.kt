package gog.my_project.data_base.migration.ast.metadata.drop_column

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationDropColumnAstMetadata : IMigrationAstMetadata {
    var tableName:   String? = null
    var name:        String? = null
    var ifExists:     Boolean = false
}
