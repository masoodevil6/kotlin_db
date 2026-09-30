package gog.my_project.data_base.migration.api.interfaces.drop_foreign_key

import gog.my_project.data_base.migration.api.interfaces.IMigrationApi
import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst

interface IMigrationDropForeignKeyApi : IMigrationApi<IMigrationDropForeignKeyAst> {
    fun tableName(table: String): IMigrationDropForeignKeyApi
    fun name(name: String): IMigrationDropForeignKeyApi
}
