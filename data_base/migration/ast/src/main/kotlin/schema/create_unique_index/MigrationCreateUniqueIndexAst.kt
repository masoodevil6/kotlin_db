package gog.my_project.data_base.migration.ast.schema.create_unique_index

import gog.my_project.data_base.migration.ast.interfaces.create_unique_index.IMigrationCreateUniqueIndexAst
import gog.my_project.data_base.migration.ast.metadata.create_unique_index.MigrationCreateUniqueIndexAstMetadata

class MigrationCreateUniqueIndexAst(
    override var metadata: MigrationCreateUniqueIndexAstMetadata = MigrationCreateUniqueIndexAstMetadata()
) : IMigrationCreateUniqueIndexAst {

}
