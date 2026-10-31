package gog.my_project.data_base.migration.ast.schema.create_table.render_migration_create_table

import gog.my_project.data_base.migration.ast.interfaces.create_table.render_migration_create_table.IMigrationRenderCreateTableAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.definition.IMigrationCreateTableDefinitionAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.ast.metadata.create_table.render_migration_create_table.MigrationRenderCreateTableAstMetadata

class MigrationRenderCreateTableAst(
    override var metadata: MigrationRenderCreateTableAstMetadata = MigrationRenderCreateTableAstMetadata()
) : IMigrationRenderCreateTableAst {
    override var migrationTableAst: IMigrationTableAst? = null;
    override var columns:           MutableList<IMigrationColumnAst> = mutableListOf();
    override var definitions:       MutableList<IMigrationCreateTableDefinitionAst<*>> = mutableListOf();
}
