package gog.my_project.data_base.migration.ast.interfaces.add_column

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.interfaces.create_table.column.IMigrationColumnAst
import gog.my_project.data_base.migration.ast.metadata.add_column.MigrationAddColumnAstMetadata

interface IMigrationAddColumnAst : IMigrationAst<MigrationAddColumnAstMetadata> {
    var columnAst: IMigrationColumnAst?
}
