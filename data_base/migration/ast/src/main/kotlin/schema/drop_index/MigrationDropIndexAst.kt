package gog.my_project.data_base.migration.ast.schema.drop_index

import gog.my_project.data_base.migration.ast.interfaces.drop_index.IMigrationDropIndexAst
import gog.my_project.data_base.migration.ast.metadata.drop_index.MigrationDropIndexAstMetadata

class MigrationDropIndexAst(
    override var metadata: MigrationDropIndexAstMetadata = MigrationDropIndexAstMetadata()
) : IMigrationDropIndexAst {

}
