package gog.my_project.data_base.migration.ast.interfaces.create_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.ast.metadata.create_foreign_key.MigrationCreateForeignKeyAstMetadata

interface IMigrationCreateForeignKeyAst : IMigrationAst<MigrationCreateForeignKeyAstMetadata> {

}
