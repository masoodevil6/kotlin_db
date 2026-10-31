package gog.my_project.data_base.migration.ast.schema.drop_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst
import gog.my_project.data_base.migration.ast.metadata.drop_foreign_key.MigrationDropForeignKeyAstMetadata

class MigrationDropForeignKeyAst(
    override var metadata: MigrationDropForeignKeyAstMetadata = MigrationDropForeignKeyAstMetadata()
) : IMigrationDropForeignKeyAst {

}
