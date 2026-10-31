package gog.my_project.data_base.migration.ast.schema.modify_column

import gog.my_project.data_base.migration.ast.interfaces.modify_column.IMigrationModifyColumnAst
import gog.my_project.data_base.migration.ast.metadata.modify_column.MigrationModifyColumnAstMetadata

class MigrationModifyColumnAst(
    override var metadata: MigrationModifyColumnAstMetadata = MigrationModifyColumnAstMetadata()
) : IMigrationModifyColumnAst {

}
