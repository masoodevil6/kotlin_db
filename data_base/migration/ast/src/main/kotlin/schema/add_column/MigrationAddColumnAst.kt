package gog.my_project.data_base.migration.ast.schema.add_column

import gog.my_project.data_base.migration.ast.interfaces.add_column.IMigrationAddColumnAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.metadata.add_column.MigrationAddColumnAstMetadata

class MigrationAddColumnAst(
    override var metadata: MigrationAddColumnAstMetadata = MigrationAddColumnAstMetadata()
) : IMigrationAddColumnAst {
    override var columnAst: IMigrationColumnAst? = null
}
