package gog.my_project.data_base.migration.ast.schema.create_table.table

import gog.my_project.data_base.migration.ast.interfaces.create_table.table.IMigrationTableAst
import gog.my_project.data_base.migration.ast.metadata.create_table.table.MigrationTableAstMetadata

class MigrationTableAst(
    override var metadata: MigrationTableAstMetadata = MigrationTableAstMetadata()
) : IMigrationTableAst {

}
