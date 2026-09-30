package gog.my_project.data_base.migration.builder.ast.create_foreign_key

import gog.my_project.data_base.core.query.reader.SqlParameter
import gog.my_project.data_base.migration.api.interfaces.create_foreign_key.IMigrationCreateForeignKeyApi
import gog.my_project.data_base.migration.ast.interfaces.create_foreign_key.IMigrationCreateForeignKeyAst
import gog.my_project.data_base.migration.ast.interfaces.foreign_key.ForeignKeyAction
import gog.my_project.data_base.migration.ast.schema.create_foreign_key.MigrationCreateForeignKeyAst

class MigrationCreateForeignKeyBuilder(
    override var params: MutableList<SqlParameter<*>> = mutableListOf(),
    override var ast: IMigrationCreateForeignKeyAst = MigrationCreateForeignKeyAst(),
) : IMigrationCreateForeignKeyApi {
    override fun tableName(table: String): IMigrationCreateForeignKeyApi = apply {
        ast.tableName = table.trim()
    }

    override fun name(name: String): IMigrationCreateForeignKeyApi = apply {
        ast.constraintName = name.trim()
    }

    override fun columns(vararg columns: String): IMigrationCreateForeignKeyApi = apply {
        ast.columnNames = columns.map(String::trim)
    }

    override fun referencesTable(table: String): IMigrationCreateForeignKeyApi = apply {
        ast.referencesTableName = table.trim()
    }

    override fun referencesColumns(vararg columns: String): IMigrationCreateForeignKeyApi = apply {
        ast.referencesColumnNames = columns.map(String::trim)
    }

    override fun onDelete(action: ForeignKeyAction): IMigrationCreateForeignKeyApi = apply {
        ast.onDelete = action
    }

    override fun onUpdate(action: ForeignKeyAction): IMigrationCreateForeignKeyApi = apply {
        ast.onUpdate = action
    }

    fun build(): IMigrationCreateForeignKeyApi {
        requireIdentifier(ast.tableName, "table")
        requireIdentifier(ast.constraintName, "constraint")
        requireIdentifier(ast.referencesTableName, "referenced table")
        val localColumns = requireNotNull(ast.columnNames) { "CREATE FOREIGN KEY requires at least one local column" }
        val referencedColumns = requireNotNull(ast.referencesColumnNames) {
            "CREATE FOREIGN KEY requires at least one referenced column"
        }
        requireValidColumns(localColumns, "local")
        requireValidColumns(referencedColumns, "referenced")
        require(localColumns.size == referencedColumns.size) {
            "CREATE FOREIGN KEY local and referenced column lists must have equal size"
        }
        return this
    }

    private fun requireValidColumns(columns: List<String>, label: String) {
        require(columns.isNotEmpty()) { "CREATE FOREIGN KEY requires at least one $label column" }
        require(columns.all(String::isNotBlank)) { "CREATE FOREIGN KEY $label column names must be non-blank" }
        for (currentIndex in columns.indices) {
            for (previousIndex in 0 until currentIndex) {
                require(!columns[currentIndex].equals(columns[previousIndex], ignoreCase = true)) {
                    "CREATE FOREIGN KEY $label column names must not contain duplicates"
                }
            }
        }
    }

    private fun requireIdentifier(value: String?, label: String) {
        require(!value.isNullOrBlank()) { "CREATE FOREIGN KEY requires a non-blank $label name" }
    }
}
