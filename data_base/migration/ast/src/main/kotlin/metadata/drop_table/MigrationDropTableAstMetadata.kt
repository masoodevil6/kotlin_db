package gog.my_project.data_base.migration.ast.metadata.drop_table

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationDropTableAstMetadata : IMigrationAstMetadata {
    var tableName: String? = null
    var ifExists: Boolean = false
}
