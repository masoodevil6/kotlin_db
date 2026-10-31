package gog.my_project.data_base.migration.ast.metadata.drop_foreign_key

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationDropForeignKeyAstMetadata : IMigrationAstMetadata {
    var tableName:      String? = null
    var constraintName: String? = null
}
