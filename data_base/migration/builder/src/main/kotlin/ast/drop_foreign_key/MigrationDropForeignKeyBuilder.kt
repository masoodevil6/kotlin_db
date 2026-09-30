package gog.my_project.data_base.migration.builder.ast.drop_foreign_key

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.drop_foreign_key.IMigrationDropForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.drop_foreign_key.IMigrationDropForeignKeyAst
import gog.my_project.data_base.migration.ast.schema.drop_foreign_key.MigrationDropForeignKeyAst

class MigrationDropForeignKeyBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationDropForeignKeyAst = MigrationDropForeignKeyAst(),
) : IMigrationDropForeignKeyApi {
    override fun tableName(table: String): IMigrationDropForeignKeyApi = apply {
        ast.tableName = table.trim()
    }

    override fun name(name: String): IMigrationDropForeignKeyApi = apply {
        ast.constraintName = name.trim()
    }

    fun build(): IMigrationDropForeignKeyApi {
        require(!ast.tableName.isNullOrBlank()) { "DROP FOREIGN KEY requires a non-blank table name" }
        require(!ast.constraintName.isNullOrBlank()) { "DROP FOREIGN KEY requires a non-blank constraint name" }
        return this
    }
}
