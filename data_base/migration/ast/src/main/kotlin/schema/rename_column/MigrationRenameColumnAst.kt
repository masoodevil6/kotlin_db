package gog.my_project.data_base.migration.ast.schema.rename_column

import gog.my_project.data_base.migration.ast.interfaces.rename_column.IMigrationRenameColumnAst
import gog.my_project.data_base.migration.ast.metadata.rename_column.MigrationRenameColumnAstMetadata

class MigrationRenameColumnAst(
    override var metadata: MigrationRenameColumnAstMetadata = MigrationRenameColumnAstMetadata()
) : IMigrationRenameColumnAst {

}
