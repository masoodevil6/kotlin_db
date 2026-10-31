package gog.my_project.data_base.migration.ast.metadata.create_table.column

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationColumnAstMetadata : IMigrationAstMetadata {
    var columnName:          String? = null
    var columnDataType:      MigrationColumnDataType? = null

    var columnDefault:       Any? = null

    var hasColumnDefault:    Boolean = false
    var columnNullable:      Boolean = false
    var columnAutoIncrement: Boolean = false
    var columnPrimary:       Boolean = false

}
