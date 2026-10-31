package gog.my_project.data_base.migration.ast.metadata.create_table.render_migration_create_table

import gog.my_project.data_base.migration.ast.metadata.interfaces.IMigrationAstMetadata

class MigrationRenderCreateTableAstMetadata : IMigrationAstMetadata {
    var ifNotExists:          Boolean = false
}
