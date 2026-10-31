package gog.my_project.data_base.migration.ast.schema.drop_table

import gog.my_project.data_base.migration.ast.interfaces.drop_table.IMigrationDropTableAst
import gog.my_project.data_base.migration.ast.metadata.drop_table.MigrationDropTableAstMetadata

class MigrationDropTableAst(
    override var metadata: MigrationDropTableAstMetadata = MigrationDropTableAstMetadata()
) : IMigrationDropTableAst {

}
