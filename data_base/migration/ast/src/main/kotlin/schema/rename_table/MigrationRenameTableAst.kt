package gog.my_project.data_base.migration.ast.schema.rename_table

import gog.my_project.data_base.migration.ast.interfaces.rename_table.IMigrationRenameTableAst
import gog.my_project.data_base.migration.ast.metadata.rename_table.MigrationRenameTableAstMetadata

class MigrationRenameTableAst(
    override var metadata: MigrationRenameTableAstMetadata = MigrationRenameTableAstMetadata()
) : IMigrationRenameTableAst {

}
