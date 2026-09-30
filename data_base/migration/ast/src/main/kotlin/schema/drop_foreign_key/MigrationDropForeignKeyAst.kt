package gog.my_project.data_base.migration.ast.schema.drop_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst

class MigrationDropForeignKeyAst : IMigrationDropForeignKeyAst {
    override var tableName: String? = null
    override var constraintName: String? = null
}
