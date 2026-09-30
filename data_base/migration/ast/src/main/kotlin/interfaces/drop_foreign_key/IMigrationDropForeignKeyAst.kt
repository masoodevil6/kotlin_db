package gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key

import gog.my_project.data_base.migration.ast.interfaces.IMigrationAst

interface IMigrationDropForeignKeyAst : IMigrationAst {
    var tableName: String?
    var constraintName: String?
}
