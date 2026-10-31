package gog.my_project.data_base.migration.ast.metadata.create_table.table

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationTableAstMetadata : IMigrationAstMetadata {
    var tableName: String? = null
}
