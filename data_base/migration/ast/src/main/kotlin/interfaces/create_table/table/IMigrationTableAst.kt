package gog.my_project.data_base.migration.ast.interfaces.create_table.table

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.metadata.create_table.table.MigrationTableAstMetadata

interface IMigrationTableAst : IMigrationAst<MigrationTableAstMetadata> {
}
