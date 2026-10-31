package gog.my_project.data_base.migration.ast.schema.create_index

import gog.my_project.data_base.migration.ast.interfaces.create_index.IMigrationCreateIndexAst
import gog.my_project.data_base.migration.ast.interfaces.create_index.IndexMethod
import gog.my_project.data_base.migration.ast.metadata.create_index.MigrationCreateIndexAstMetadata

class MigrationCreateIndexAst(
    override var metadata: MigrationCreateIndexAstMetadata = MigrationCreateIndexAstMetadata()
) : IMigrationCreateIndexAst {

}
