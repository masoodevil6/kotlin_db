package gog.my_project.data_base.migration.ast.metadata.modify_column

import gog.my_project.data_base.migration.ast.interfaces.modify_column.ColumnNullability
import gog.my_project.data_base.migration.ast.interfaces.modify_column.DefaultDefinition
import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationModifyColumnAstMetadata : IMigrationAstMetadata {
    var tableName:           String? = null
    var columnName:          String? = null
    var columnDataType:      MigrationColumnDataType? = null
    var nullability:         ColumnNullability? = null
    var defaultDefinition:   DefaultDefinition? = null
    var autoIncrement:       Boolean = false
}
