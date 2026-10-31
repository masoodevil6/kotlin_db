package gog.my_project.data_base.migration.ast.schema.create_table.column

import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.metadata.create_table.column.MigrationColumnAstMetadata
import gog.my_project.data_base.migration.params.data_types.MigrationColumnDataType

class MigrationColumnAst(
    override var metadata: MigrationColumnAstMetadata = MigrationColumnAstMetadata()
) : IMigrationColumnAst {


}
