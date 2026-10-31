package gog.my_project.data_base.migration.ast.schema.drop_column

import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst
import gog.my_project.data_base.migration.ast.metadata.drop_column.MigrationDropColumnAstMetadata

class MigrationDropColumnAst(
    override var metadata: MigrationDropColumnAstMetadata = MigrationDropColumnAstMetadata()
) : IMigrationDropColumnAst {

}
