package gog.my_project.data_base.migration.ast.schema.create_multi_column_index

import gog.my_project.data_base.migration.ast.interfaces.create_multi_column_index.IMigrationCreateMultiColumnIndexAst
import gog.my_project.data_base.migration.ast.metadata.create_multi_column_index.MigrationCreateMultiColumnIndexAstMetadata

class MigrationCreateMultiColumnIndexAst(
    override var metadata: MigrationCreateMultiColumnIndexAstMetadata = MigrationCreateMultiColumnIndexAstMetadata()
) : IMigrationCreateMultiColumnIndexAst {

}
