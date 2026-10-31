package gog.my_project.data_base.migration.ast.metadata.rename_table

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationRenameTableAstMetadata : IMigrationAstMetadata {
    var fromTableName:  String? = null
    var toTableName:    String? = null
}
