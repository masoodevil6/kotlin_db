package gog.my_project.data_base.migration.ast.metadata.add_column

import gog.my_project.data_base.migration.ast.metadata.create_table.column.MigrationColumnAstMetadata
import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationAddColumnAstMetadata : IMigrationAstMetadata {
    var tableName: String? = null
}
