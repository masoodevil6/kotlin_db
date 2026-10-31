package gog.my_project.data_base.migration.ast.schema.create_full_text_index

import gog.my_project.data_base.migration.ast.interfaces.create_full_text_index.IMigrationCreateFullTextIndexAst
import gog.my_project.data_base.migration.ast.metadata.create_full_text_index.MigrationCreateFullTextIndexAstMetadata

class MigrationCreateFullTextIndexAst(
    override var metadata: MigrationCreateFullTextIndexAstMetadata = MigrationCreateFullTextIndexAstMetadata()
) : IMigrationCreateFullTextIndexAst {

}
