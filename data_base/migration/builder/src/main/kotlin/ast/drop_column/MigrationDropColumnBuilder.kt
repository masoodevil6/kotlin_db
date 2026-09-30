package gog.my_project.data_base.migration.builder.ast.drop_column

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.drop_column.IMigrationDropColumnApi
import gog.my_project.data_base.migration.ast.interfaces.drop_column.IMigrationDropColumnAst
import gog.my_project.data_base.migration.ast.schema.drop_column.MigrationDropColumnAst

class MigrationDropColumnBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationDropColumnAst = MigrationDropColumnAst(),
) : IMigrationDropColumnApi {

    override fun tableName(table: String): IMigrationDropColumnApi {
        ast.tableName = table.trim().also {
            require(it.isNotEmpty()) { "DROP COLUMN requires a non-blank table name" }
        }
        return this
    }

    override fun name(name: String): IMigrationDropColumnApi {
        ast.name = name.trim().also {
            require(it.isNotEmpty()) { "DROP COLUMN requires a non-blank column name" }
        }
        return this
    }

    override fun ifExists(): IMigrationDropColumnApi {
        ast.ifExists = true
        return this
    }

    fun build(): IMigrationDropColumnApi {
        require(!ast.tableName.isNullOrBlank()) {
            "DROP COLUMN requires a non-blank table name"
        }
        require(!ast.name.isNullOrBlank()) {
            "DROP COLUMN requires a non-blank column name"
        }
        return this
    }
}
