package gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.IMigrationCreateTableDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.ast.metadata.create_table.render_migration_create_table.MigrationRenderCreateTableAstMetadata

interface IMigrationRenderCreateTableAst : IMigrationAst<MigrationRenderCreateTableAstMetadata> {

    var migrationTableAst:    IMigrationTableAst?;
    var columns:              MutableList<IMigrationColumnAst>;
    var definitions:          MutableList<IMigrationCreateTableDefinitionAst<*>>;
}
