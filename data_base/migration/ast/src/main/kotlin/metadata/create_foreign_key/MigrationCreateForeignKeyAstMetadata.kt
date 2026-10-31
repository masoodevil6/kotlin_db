package gog.my_project.data_base.migration.ast.metadata.create_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationCreateForeignKeyAstMetadata : IMigrationAstMetadata {
    var tableName:             String? = null
    var constraintName:        String? = null
    var columnNames:           List<String>? = null
    var referencesTableName:   String? = null
    var referencesColumnNames: List<String>? = null
    var onDelete:              ForeignKeyAction? = null
    var onUpdate:              ForeignKeyAction? = null
}
