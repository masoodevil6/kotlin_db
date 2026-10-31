package gog.my_project.data_base.migration.ast.metadata.rename_column

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationRenameColumnAstMetadata : IMigrationAstMetadata {
    var tableName:   String? = null
    var name:        String? = null
    var to:          String? = null
}
